package com.ll.metrics.latency.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ll.metrics.latency.constants.LatencyClockedConstants;
import com.ll.metrics.latency.maven.samples.SampleTimedClass;
import com.ll.metrics.latency.timer.Timer;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

class LatencyScanMojoTest {
  @Test
  void executeScansInjectsTimerFieldAndWritesDescriptor(@TempDir Path outputDirectory)
      throws Exception {
    copyClassToOutputDirectory(SampleTimedClass.class, outputDirectory);
    LatencyScanMojo mojo = new LatencyScanMojo();
    setOutputDirectory(mojo, outputDirectory.toFile());
    Path reportDirectory = outputDirectory.resolve("reports");
    setReportDirectory(mojo, reportDirectory.toFile());

    mojo.execute();

    assertEquals(
        SampleTimedClass.class.getName(), Files.readString(descriptor(outputDirectory)).trim());
    assertTrue(injectedTimerFieldExists(outputDirectory, SampleTimedClass.class));
    assertTrue(injectedBindMethodExists(outputDirectory, SampleTimedClass.class));
    String report = Files.readString(reportDirectory.resolve("instrumentation-report.txt"));
    assertTrue(report.contains("LatencyClocked instrumentation report"));
    assertTrue(report.contains("instrumented|" + SampleTimedClass.class.getName()));
  }

  @Test
  void executeFailsForInvalidTimedUsage(@TempDir Path outputDirectory) throws Exception {
    GoldenFixtureCompiler.compile(outputDirectory, "UnsupportedNativeTimedSample.java");
    LatencyScanMojo mojo = new LatencyScanMojo();
    setOutputDirectory(mojo, outputDirectory.toFile());
    setReportDirectory(mojo, outputDirectory.resolve("reports").toFile());

    MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::execute);

    assertTrue(
        exception
            .getMessage()
            .contains("golden.UnsupportedNativeTimedSample.nativeTimedMethod()V"));
    assertTrue(
        exception
            .getMessage()
            .contains(
                "@Timed cannot be applied to native methods because there is no bytecode body"));
  }

  private static void setOutputDirectory(LatencyScanMojo mojo, File outputDirectory)
      throws ReflectiveOperationException {
    Field field = LatencyScanMojo.class.getDeclaredField("outputDirectory");
    field.setAccessible(true);
    field.set(mojo, outputDirectory);
  }

  private static void setReportDirectory(LatencyScanMojo mojo, File reportDirectory)
      throws ReflectiveOperationException {
    Field field = LatencyScanMojo.class.getDeclaredField("reportDirectory");
    field.setAccessible(true);
    field.set(mojo, reportDirectory);
  }

  private static void copyClassToOutputDirectory(Class<?> sourceClass, Path outputDirectory)
      throws IOException {
    Path classFile = classFile(outputDirectory, sourceClass);
    Files.createDirectories(classFile.getParent());
    Files.copy(classFile(Path.of("target", "test-classes"), sourceClass), classFile);
  }

  private static boolean injectedTimerFieldExists(Path outputDirectory, Class<?> sourceClass)
      throws IOException {
    boolean[] found = {false};
    try (InputStream inputStream = Files.newInputStream(classFile(outputDirectory, sourceClass))) {
      ClassReader reader = new ClassReader(inputStream);
      reader.accept(
          new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                int access, String name, String descriptor, String signature, Object value) {
              if (name.equals("__latency_clocked_timer_0")
                  && descriptor.equals(Type.getDescriptor(Timer.class))
                  && (access & Opcodes.ACC_STATIC) != 0) {
                found[0] = true;
              }
              return null;
            }
          },
          ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
    return found[0];
  }

  private static boolean injectedBindMethodExists(Path outputDirectory, Class<?> sourceClass)
      throws IOException {
    boolean[] found = {false};
    try (InputStream inputStream = Files.newInputStream(classFile(outputDirectory, sourceClass))) {
      ClassReader reader = new ClassReader(inputStream);
      reader.accept(
          new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
              if (name.equals("__latency_clocked$bind")
                  && (access & Opcodes.ACC_STATIC) != 0
                  && (access & Opcodes.ACC_SYNTHETIC) != 0) {
                found[0] = true;
              }
              return null;
            }
          },
          ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
    return found[0];
  }

  private static Path classFile(Path outputDirectory, Class<?> sourceClass) {
    return outputDirectory.resolve(
        sourceClass
                .getName()
                .replace(
                    LatencyClockedConstants.CLASS_NAME_SEPARATOR,
                    LatencyClockedConstants.RESOURCE_PATH_SEPARATOR)
            + LatencyClockedConstants.CLASS_FILE_EXTENSION);
  }

  private static Path descriptor(Path outputDirectory) {
    return outputDirectory
        .resolve(LatencyClockedConstants.DESCRIPTOR_ROOT)
        .resolve(LatencyClockedConstants.DESCRIPTOR_DIRECTORY)
        .resolve(LatencyClockedConstants.DESCRIPTOR_FILE);
  }
}
