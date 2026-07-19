package com.ll.metrics.latency.maven;

import com.ll.metrics.latency.constants.LatencyClockedConstants;
import com.ll.metrics.latency.maven.asm.TimedClassAnalyser;
import com.ll.metrics.latency.maven.asm.TimerFieldInjector;
import com.ll.metrics.latency.maven.model.TimedClassMetadata;
import com.ll.metrics.latency.maven.model.TimedMethodDescriptorEntry;
import com.ll.metrics.latency.maven.model.TimedMethodMetadata;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

final class LatencyClockedInstrumenter {
  private static final TimedClassAnalyser CLASS_ANALYSER =
      new TimedClassAnalyser(new TimerIdResolver());
  private static final TimerFieldInjector TIMER_FIELD_INJECTOR = new TimerFieldInjector();

  private LatencyClockedInstrumenter() {}

  static Map<Path, List<TimedMethodDescriptorEntry>> scan(Path outputDirectory) throws IOException {
    Objects.requireNonNull(outputDirectory, "outputDirectory");

    Map<Path, List<TimedMethodDescriptorEntry>> timedMethodsByClassFile = new LinkedHashMap<>();
    for (Path classFile : classFiles(outputDirectory)) {
      TimedClassMetadata metadata = CLASS_ANALYSER.analyse(classFile);
      List<TimedMethodDescriptorEntry> timedMethods = timedMethodEntries(metadata);
      if (!timedMethods.isEmpty()) {
        timedMethodsByClassFile.put(classFile, timedMethods);
      }
    }
    return timedMethodsByClassFile;
  }

  static InjectionResult instrument(
      Map<Path, List<TimedMethodDescriptorEntry>> timedMethodsByClassFile) throws IOException {
    Objects.requireNonNull(timedMethodsByClassFile, "timedMethodsByClassFile");

    int injectedFields = 0;
    int skippedFields = 0;
    int injectedBindMethods = 0;
    int skippedBindMethods = 0;
    int instrumentedMethods = 0;
    int skippedInstrumentedMethods = 0;
    for (Map.Entry<Path, List<TimedMethodDescriptorEntry>> classEntry :
        timedMethodsByClassFile.entrySet()) {
      TimerFieldInjector.FieldInjectionResult result =
          TIMER_FIELD_INJECTOR.inject(classEntry.getKey(), classEntry.getValue());
      injectedFields += result.injectedFields();
      skippedFields += result.skippedFields();
      injectedBindMethods += result.injectedBindMethods();
      skippedBindMethods += result.skippedBindMethods();
      instrumentedMethods += result.instrumentedMethods();
      skippedInstrumentedMethods += result.skippedInstrumentedMethods();
    }
    return new InjectionResult(
        injectedFields,
        skippedFields,
        injectedBindMethods,
        skippedBindMethods,
        instrumentedMethods,
        skippedInstrumentedMethods);
  }

  static void generateInstrumentedClassIndexResource(
      Path outputDirectory, List<TimedMethodDescriptorEntry> timedMethods) throws IOException {
    writeInstrumentedClassIndexToFile(outputDirectory, timedMethods);
  }

  static Path writeInstrumentationReportToFile(
      Path reportDirectory,
      Map<Path, List<TimedMethodDescriptorEntry>> timedMethodsByClassFile,
      InjectionResult injectionResult)
      throws IOException {
    Objects.requireNonNull(reportDirectory, "reportDirectory");
    Objects.requireNonNull(timedMethodsByClassFile, "timedMethodsByClassFile");
    Objects.requireNonNull(injectionResult, "injectionResult");

    Files.createDirectories(reportDirectory);
    Path report = reportDirectory.resolve("instrumentation-report.txt");
    Files.write(report, instrumentationReport(timedMethodsByClassFile, injectionResult));
    return report;
  }

  private static List<TimedMethodDescriptorEntry> timedMethodEntries(TimedClassMetadata metadata) {
    return metadata.timedMethods().stream()
        .map(method -> timedMethodEntry(metadata.className(), method))
        .toList();
  }

  private static TimedMethodDescriptorEntry timedMethodEntry(
      String className, TimedMethodMetadata method) {
    return new TimedMethodDescriptorEntry(
        className,
        method.methodName(),
        method.methodDescriptor(),
        method.generatedTimerFieldName(),
        method.resolvedTimerId());
  }

  private static List<Path> classFiles(Path outputDirectory) throws IOException {
    if (!Files.isDirectory(outputDirectory)) {
      return List.of();
    }

    try (Stream<Path> paths = Files.walk(outputDirectory)) {
      return paths
          .filter(
              path ->
                  Files.isRegularFile(path)
                      && path.toString().endsWith(LatencyClockedConstants.CLASS_FILE_EXTENSION))
          .sorted(Comparator.comparing(Path::toString))
          .toList();
    }
  }

  private static void writeInstrumentedClassIndexToFile(
      Path outputDirectory,
      Collection<TimedMethodDescriptorEntry> timedMethods)
      throws IOException {
    Objects.requireNonNull(outputDirectory, "outputDirectory");
    Objects.requireNonNull(timedMethods, "timedMethods");

    Path indexDirectory =
        outputDirectory
            .resolve(LatencyClockedConstants.INSTRUMENTED_CLASS_INDEX_ROOT)
            .resolve(LatencyClockedConstants.INSTRUMENTED_CLASS_INDEX_DIRECTORY);
    Path index =
        indexDirectory.resolve(LatencyClockedConstants.INSTRUMENTED_CLASS_INDEX_FILE);
    if (timedMethods.isEmpty()) {
      Files.deleteIfExists(index);
      return;
    }

    Files.createDirectories(indexDirectory);

    List<String> lines =
        new LinkedHashSet<>(
                timedMethods.stream().map(TimedMethodDescriptorEntry::className).toList())
            .stream().toList();
    Files.write(index, lines, StandardCharsets.UTF_8);
  }

  private static List<String> instrumentationReport(
      Map<Path, List<TimedMethodDescriptorEntry>> timedMethodsByClassFile,
      InjectionResult injectionResult) {
    List<String> lines = new ArrayList<>();
    lines.add("LatencyClocked instrumentation report");
    lines.add("classesWithTimedMethods=" + timedMethodsByClassFile.size());
    int timedMethodCount =
        timedMethodsByClassFile.values().stream().mapToInt(List::size).sum();
    lines.add("timedMethods=" + timedMethodCount);
    lines.add("injectedFields=" + injectionResult.injectedFields());
    lines.add("skippedFields=" + injectionResult.skippedFields());
    lines.add("injectedBindMethods=" + injectionResult.injectedBindMethods());
    lines.add("skippedBindMethods=" + injectionResult.skippedBindMethods());
    lines.add("instrumentedMethods=" + injectionResult.instrumentedMethods());
    lines.add("skippedInstrumentedMethods=" + injectionResult.skippedInstrumentedMethods());
    lines.add("");
    lines.add("status|className|methodName|methodDescriptor|timerFieldName|timerId|classFile");
    for (Map.Entry<Path, List<TimedMethodDescriptorEntry>> classEntry :
        timedMethodsByClassFile.entrySet()) {
      for (TimedMethodDescriptorEntry timedMethod : classEntry.getValue()) {
        lines.add(
            String.join(
                "|",
                "instrumented",
                timedMethod.className(),
                timedMethod.methodName(),
                timedMethod.methodDescriptor(),
                timedMethod.timerFieldName(),
                timedMethod.timerId(),
                classEntry.getKey().toString()));
      }
    }
    return lines;
  }

  record InjectionResult(
      int injectedFields,
      int skippedFields,
      int injectedBindMethods,
      int skippedBindMethods,
      int instrumentedMethods,
      int skippedInstrumentedMethods) {}
}
