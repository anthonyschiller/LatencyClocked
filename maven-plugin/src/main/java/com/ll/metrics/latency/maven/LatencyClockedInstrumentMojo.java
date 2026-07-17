package com.ll.metrics.latency.maven;

import com.ll.metrics.latency.maven.model.TimedMethodDescriptorEntry;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Maven goal that scans compiled classes, instruments {@code @Timed} methods, and generates the
 * runtime instrumented class-index.
 */
@Mojo(name = "instrument", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true)
public final class LatencyClockedInstrumentMojo extends AbstractMojo {
  @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true, required = true)
  private File outputDirectory;

  @Parameter(defaultValue = "${project.build.directory}/latency-clocked", readonly = true)
  private File reportDirectory;

  @Parameter(property = "latency-clocked.verbose", defaultValue = "false")
  private boolean verbose;

  @Override
  public void execute() throws MojoExecutionException {
    try {
      Map<Path, List<TimedMethodDescriptorEntry>> timedMethodsByClassFile =
          LatencyClockedInstrumenter.scan(outputDirectory.toPath());
      LatencyClockedInstrumenter.InjectionResult injectionResult =
          LatencyClockedInstrumenter.instrument(timedMethodsByClassFile);
      List<TimedMethodDescriptorEntry> timedMethods =
          timedMethodsByClassFile.values().stream().flatMap(List::stream).toList();
      LatencyClockedInstrumenter.generateInstrumentedClassIndexResource(
          outputDirectory.toPath(), timedMethods);
      Path report =
          LatencyClockedInstrumenter.writeInstrumentationReportToFile(
              reportDirectory.toPath(), timedMethodsByClassFile, injectionResult);
      logTimedMethodDetails(timedMethodsByClassFile);
      int timedMethodCount = timedMethodsByClassFile.values().stream().mapToInt(List::size).sum();
      getLog().info("Discovered " + timedMethodCount + " timed methods.");
      getLog().info("Wrote latency instrumentation report to " + report);
      getLog()
          .info(
              "Injected "
                  + injectionResult.injectedFields()
                  + " latency timer fields; skipped "
                  + injectionResult.skippedFields()
                  + " existing fields.");
      getLog()
          .info(
              "Injected "
                  + injectionResult.injectedBindMethods()
                  + " latency bind methods; skipped "
                  + injectionResult.skippedBindMethods()
                  + " existing bind methods.");
      getLog()
          .info(
              "Instrumented "
                  + injectionResult.instrumentedMethods()
                  + " timed methods; skipped "
                  + injectionResult.skippedInstrumentedMethods()
                  + " already instrumented methods.");
    } catch (IllegalArgumentException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to instrument latency-clocked classes", e);
    }
  }

  private void logTimedMethodDetails(
      Map<Path, List<TimedMethodDescriptorEntry>> timedMethodsByClassFile) {
    logDetail("Scanned " + outputDirectory.toPath() + " for latency-clocked class files.");
    logDetail("Found " + timedMethodsByClassFile.size() + " classes containing @Timed methods.");
    for (Map.Entry<Path, List<TimedMethodDescriptorEntry>> classEntry :
        timedMethodsByClassFile.entrySet()) {
      logDetail(
          "Class "
              + classEntry.getValue().getFirst().className()
              + " has "
              + classEntry.getValue().size()
              + " timed methods in "
              + classEntry.getKey());
      for (TimedMethodDescriptorEntry timedMethod : classEntry.getValue()) {
        logDetail(
            "Instrumented "
                + timedMethod.className()
                + "#"
                + timedMethod.methodName()
                + timedMethod.methodDescriptor()
                + " -> "
                + timedMethod.timerId()
                + " using "
                + timedMethod.fieldName());
      }
    }
  }

  private void logDetail(String message) {
    if (verbose) {
      getLog().info(message);
    } else {
      getLog().debug(message);
    }
  }
}
