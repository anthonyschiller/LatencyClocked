package com.ll.metrics.latency.maven;

import java.io.IOException;
import java.nio.file.Path;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

final class GoldenFixtureCompiler {
  private GoldenFixtureCompiler() {}

  static void compile(Path outputDirectory, String sourceFileName) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("JDK compiler is required to compile test fixtures");
    }
    Path source = Path.of("src", "test", "resources", "golden", sourceFileName);
    int result =
        compiler.run(
            null,
            null,
            null,
            "-classpath",
            System.getProperty("java.class.path"),
            "--release",
            "21",
            "-d",
            outputDirectory.toString(),
            source.toString());
    if (result != 0) {
      throw new IllegalStateException("Failed to compile test fixture " + sourceFileName);
    }
  }
}
