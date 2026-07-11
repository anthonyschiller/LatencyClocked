package com.ll.metrics.latency.maven.asm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ll.metrics.latency.constants.LatencyClockedConstants;
import com.ll.metrics.latency.maven.TimerIdResolver;
import com.ll.metrics.latency.maven.model.TimedClassMetadata;
import com.ll.metrics.latency.maven.model.TimedMethodMetadata;
import com.ll.metrics.latency.maven.samples.SampleTimedClass;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class TimedClassAnalyserTest {
  private static final String TIMED_DESCRIPTOR = "Lcom/ll/metrics/latency/annotations/Timed;";

  private final TimedClassAnalyser analyser = new TimedClassAnalyser(new TimerIdResolver());

  @Test
  void detectsInstanceTimedMethods() throws IOException {
    TimedClassMetadata metadata = analyser.analyse(classFile(SampleTimedClass.class));
    Map<String, TimedMethodMetadata> methodsByName = methodsByName(metadata);

    assertTrue(methodsByName.containsKey("timedMethod"));
    assertFalse(methodsByName.get("timedMethod").isStatic());
    assertEquals(
        SampleTimedClass.class.getName() + "#timedMethod()V",
        methodsByName.get("timedMethod").resolvedTimerId());
  }

  @Test
  void detectsStaticTimedMethods() throws IOException {
    TimedClassMetadata metadata = analyser.analyse(classFile(SampleTimedClass.class));
    TimedMethodMetadata method = methodsByName(metadata).get("staticTimedMethod");

    assertTrue(method.isStatic());
    assertEquals(
        SampleTimedClass.class.getName() + "#staticTimedMethod()V", method.resolvedTimerId());
  }

  @Test
  void resolvesGeneratedMethodId() throws IOException {
    TimedClassMetadata metadata = analyser.analyse(classFile(SampleTimedClass.class));
    TimedMethodMetadata method = methodsByName(metadata).get("timedMethodWithGeneratedId");

    assertEquals(
        SampleTimedClass.class.getName() + "#timedMethodWithGeneratedId()V",
        method.resolvedTimerId());
  }

  @Test
  void resolvesOverloadedTimedMethods() throws IOException {
    TimedClassMetadata metadata = analyser.analyse(classFile(SampleTimedClass.class));

    assertTrue(
        metadata.timedMethods().stream()
            .anyMatch(
                method ->
                    method
                        .resolvedTimerId()
                        .equals(SampleTimedClass.class.getName() + "#overloaded(I)V")));
    assertTrue(
        metadata.timedMethods().stream()
            .anyMatch(
                method ->
                    method
                        .resolvedTimerId()
                        .equals(
                            SampleTimedClass.class.getName()
                                + "#overloaded(Ljava/lang/String;)V")));
  }

  @Test
  void rejectsAnnotatedConstructors(@TempDir Path outputDirectory) throws IOException {
    Path classFile = annotatedSpecialMethod(outputDirectory, "<init>", "()V", Opcodes.ACC_PUBLIC);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> analyser.analyse(classFile));

    assertTrue(
        exception
            .getMessage()
            .contains("Instrumentation unsupported for golden.GeneratedUnsupported.<init>()V"));
    assertTrue(
        exception
            .getMessage()
            .contains("@Timed cannot be applied to constructors because constructor bytecode"));
  }

  @Test
  void rejectsAnnotatedClassInitialisers(@TempDir Path outputDirectory) throws IOException {
    Path classFile = annotatedSpecialMethod(outputDirectory, "<clinit>", "()V", Opcodes.ACC_STATIC);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> analyser.analyse(classFile));

    assertTrue(
        exception
            .getMessage()
            .contains("Instrumentation unsupported for golden.GeneratedUnsupported.<clinit>()V"));
    assertTrue(exception.getMessage().contains("@Timed cannot be applied to class initialisers."));
  }

  private static Map<String, TimedMethodMetadata> methodsByName(TimedClassMetadata metadata) {
    return metadata.timedMethods().stream()
        .collect(
            Collectors.toMap(
                TimedMethodMetadata::methodName, method -> method, (first, ignored) -> first));
  }

  private static Path classFile(Class<?> type) {
    return Path.of(
        "target",
        "test-classes",
        type.getName()
                .replace(
                    LatencyClockedConstants.CLASS_NAME_SEPARATOR,
                    LatencyClockedConstants.RESOURCE_PATH_SEPARATOR)
            + LatencyClockedConstants.CLASS_FILE_EXTENSION);
  }

  private static Path annotatedSpecialMethod(
      Path outputDirectory, String methodName, String descriptor, int access) throws IOException {
    ClassWriter writer = new ClassWriter(0);
    writer.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "golden/GeneratedUnsupported",
        null,
        "java/lang/Object",
        null);
    MethodVisitor methodVisitor = writer.visitMethod(access, methodName, descriptor, null, null);
    AnnotationVisitor annotationVisitor = methodVisitor.visitAnnotation(TIMED_DESCRIPTOR, false);
    annotationVisitor.visitEnd();
    methodVisitor.visitCode();
    if ("<init>".equals(methodName)) {
      methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
      methodVisitor.visitMethodInsn(
          Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    }
    methodVisitor.visitInsn(Opcodes.RETURN);
    methodVisitor.visitMaxs(1, 1);
    methodVisitor.visitEnd();
    writer.visitEnd();

    Path classFile = outputDirectory.resolve("golden/GeneratedUnsupported.class");
    Files.createDirectories(classFile.getParent());
    Files.write(classFile, writer.toByteArray());
    return classFile;
  }
}
