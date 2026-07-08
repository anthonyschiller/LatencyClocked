package com.ll.metrics.latency.maven;

import com.ll.metrics.latency.constants.LatencyClockedConstants;
import com.ll.metrics.latency.maven.asm.TimedClassAnalyser;
import com.ll.metrics.latency.maven.asm.TimerFieldInjector;
import com.ll.metrics.latency.maven.model.LatencyDescriptorEntry;
import com.ll.metrics.latency.maven.model.TimedClassMetadata;
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

final class LatencyDescriptorGenerator {
  private static final TimedClassAnalyser CLASS_ANALYSER =
      new TimedClassAnalyser(new TimerIdResolver());
  private static final TimerFieldInjector TIMER_FIELD_INJECTOR = new TimerFieldInjector();

  private LatencyDescriptorGenerator() {}

  static Map<Path, List<LatencyDescriptorEntry>> scan(Path outputDirectory) throws IOException {
    Objects.requireNonNull(outputDirectory, "outputDirectory");

    Map<Path, List<LatencyDescriptorEntry>> descriptorsByClassFile = new LinkedHashMap<>();
    for (Path classFile : classFiles(outputDirectory)) {
      TimedClassMetadata metadata = CLASS_ANALYSER.analyse(classFile);
      List<LatencyDescriptorEntry> entries = descriptorEntries(metadata);
      if (!entries.isEmpty()) {
        descriptorsByClassFile.put(classFile, entries);
      }
    }
    return descriptorsByClassFile;
  }

  static InjectionResult injectTimerFields(
      Map<Path, List<LatencyDescriptorEntry>> entriesByClassFile) throws IOException {
    Objects.requireNonNull(entriesByClassFile, "entriesByClassFile");

    int injectedFields = 0;
    int skippedFields = 0;
    int injectedBindMethods = 0;
    int skippedBindMethods = 0;
    int instrumentedMethods = 0;
    int skippedInstrumentedMethods = 0;
    for (Map.Entry<Path, List<LatencyDescriptorEntry>> classEntry : entriesByClassFile.entrySet()) {
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

  static void writeDescriptor(Path outputDirectory, List<LatencyDescriptorEntry> descriptors)
      throws IOException {
    writeIndex(outputDirectory, descriptors);
  }

  static Path writeInstrumentationReport(
      Path reportDirectory,
      Map<Path, List<LatencyDescriptorEntry>> entriesByClassFile,
      InjectionResult injectionResult)
      throws IOException {
    Objects.requireNonNull(reportDirectory, "reportDirectory");
    Objects.requireNonNull(entriesByClassFile, "entriesByClassFile");
    Objects.requireNonNull(injectionResult, "injectionResult");

    Files.createDirectories(reportDirectory);
    Path report = reportDirectory.resolve("instrumentation-report.txt");
    Files.write(report, instrumentationReport(entriesByClassFile, injectionResult));
    return report;
  }

  private static List<LatencyDescriptorEntry> descriptorEntries(TimedClassMetadata metadata) {
    return metadata.timedMethods().stream()
        .map(method -> descriptorEntry(metadata.className(), method))
        .toList();
  }

  private static LatencyDescriptorEntry descriptorEntry(
      String className, TimedMethodMetadata method) {
    return new LatencyDescriptorEntry(
        className,
        method.methodName(),
        method.methodDescriptor(),
        method.generatedFieldName(),
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

  private static void writeIndex(
      Path outputDirectory, Collection<LatencyDescriptorEntry> descriptors) throws IOException {
    Objects.requireNonNull(outputDirectory, "outputDirectory");
    Objects.requireNonNull(descriptors, "descriptors");

    Path descriptorDirectory =
        outputDirectory
            .resolve(LatencyClockedConstants.DESCRIPTOR_ROOT)
            .resolve(LatencyClockedConstants.DESCRIPTOR_DIRECTORY);
    Path descriptor = descriptorDirectory.resolve(LatencyClockedConstants.DESCRIPTOR_FILE);
    if (descriptors.isEmpty()) {
      Files.deleteIfExists(descriptor);
      return;
    }

    Files.createDirectories(descriptorDirectory);

    List<String> lines =
        new LinkedHashSet<>(descriptors.stream().map(LatencyDescriptorEntry::className).toList())
            .stream().toList();
    Files.write(descriptor, lines, StandardCharsets.UTF_8);
  }

  private static List<String> instrumentationReport(
      Map<Path, List<LatencyDescriptorEntry>> entriesByClassFile, InjectionResult injectionResult) {
    List<String> lines = new ArrayList<>();
    lines.add("LatencyClocked instrumentation report");
    lines.add("classesWithTimedMethods=" + entriesByClassFile.size());
    lines.add("timedMethods=" + entriesByClassFile.values().stream().mapToInt(List::size).sum());
    lines.add("injectedFields=" + injectionResult.injectedFields());
    lines.add("skippedFields=" + injectionResult.skippedFields());
    lines.add("injectedBindMethods=" + injectionResult.injectedBindMethods());
    lines.add("skippedBindMethods=" + injectionResult.skippedBindMethods());
    lines.add("instrumentedMethods=" + injectionResult.instrumentedMethods());
    lines.add("skippedInstrumentedMethods=" + injectionResult.skippedInstrumentedMethods());
    lines.add("");
    lines.add("status|className|methodName|methodDescriptor|fieldName|timerId|classFile");
    for (Map.Entry<Path, List<LatencyDescriptorEntry>> classEntry : entriesByClassFile.entrySet()) {
      for (LatencyDescriptorEntry entry : classEntry.getValue()) {
        lines.add(
            String.join(
                "|",
                "instrumented",
                entry.className(),
                entry.methodName(),
                entry.methodDescriptor(),
                entry.fieldName(),
                entry.timerId(),
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
