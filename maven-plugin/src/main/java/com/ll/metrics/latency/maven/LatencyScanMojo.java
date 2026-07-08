package com.ll.metrics.latency.maven;

import com.ll.metrics.latency.maven.model.LatencyDescriptorEntry;
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

/** Maven goal that scans compiled classes and writes the latency descriptor index. */
@Mojo(name = "scan", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true)
public final class LatencyScanMojo extends AbstractMojo {
  @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true, required = true)
  private File outputDirectory;

  @Parameter(defaultValue = "${project.build.directory}/latency-clocked", readonly = true)
  private File reportDirectory;

  @Parameter(property = "latency-clocked.verbose", defaultValue = "false")
  private boolean verbose;

  @Override
  public void execute() throws MojoExecutionException {
    try {
      Map<Path, List<LatencyDescriptorEntry>> entries =
          LatencyDescriptorGenerator.scan(outputDirectory.toPath());
      LatencyDescriptorGenerator.InjectionResult injectionResult =
          LatencyDescriptorGenerator.injectTimerFields(entries);
      List<LatencyDescriptorEntry> descriptors =
          entries.values().stream().flatMap(List::stream).toList();
      LatencyDescriptorGenerator.writeDescriptor(outputDirectory.toPath(), descriptors);
      Path report =
          LatencyDescriptorGenerator.writeInstrumentationReport(
              reportDirectory.toPath(), entries, injectionResult);
      logDescriptorDetails(entries);
      int entryCount = entries.values().stream().mapToInt(List::size).sum();
      getLog().info("Generated " + entryCount + " latency descriptor entries.");
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
      throw new MojoExecutionException("Failed to generate latency descriptor index", e);
    }
  }

  private void logDescriptorDetails(Map<Path, List<LatencyDescriptorEntry>> entries) {
    logDetail("Scanned " + outputDirectory.toPath() + " for latency-clocked class files.");
    logDetail("Found " + entries.size() + " classes containing @Timed methods.");
    for (Map.Entry<Path, List<LatencyDescriptorEntry>> classEntry : entries.entrySet()) {
      logDetail(
          "Class "
              + classEntry.getValue().getFirst().className()
              + " has "
              + classEntry.getValue().size()
              + " timed methods in "
              + classEntry.getKey());
      for (LatencyDescriptorEntry entry : classEntry.getValue()) {
        logDetail(
            "Instrumented "
                + entry.className()
                + "#"
                + entry.methodName()
                + entry.methodDescriptor()
                + " -> "
                + entry.timerId()
                + " using "
                + entry.fieldName());
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
