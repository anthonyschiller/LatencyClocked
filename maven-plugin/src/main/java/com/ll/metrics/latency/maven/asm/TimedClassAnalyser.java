package com.ll.metrics.latency.maven.asm;

import com.ll.metrics.latency.constants.LatencyClockedConstants;
import com.ll.metrics.latency.maven.TimerIdResolver;
import com.ll.metrics.latency.maven.model.TimedClassMetadata;
import com.ll.metrics.latency.maven.model.TimedMethodCandidate;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Reads class bytecode and returns metadata for methods annotated with {@code @Timed}. */
public final class TimedClassAnalyser {
  private static final String TIMED_DESCRIPTOR = "Lcom/ll/metrics/latency/annotations/Timed;";

  private final TimerIdResolver timerIdResolver;

  public TimedClassAnalyser(TimerIdResolver timerIdResolver) {
    this.timerIdResolver = Objects.requireNonNull(timerIdResolver, "timerIdResolver");
  }

  /** Analyses one compiled class file. */
  public TimedClassMetadata analyse(Path classFile) throws IOException {
    Objects.requireNonNull(classFile, "classFile");
    try (InputStream inputStream = Files.newInputStream(classFile)) {
      ClassReader reader = new ClassReader(inputStream);
      TimedClassVisitor visitor = new TimedClassVisitor();
      reader.accept(
          visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
      return new TimedClassMetadata(
          visitor.className(),
          timerIdResolver.resolve(visitor.className(), visitor.timedMethods()));
    }
  }

  private static final class TimedClassVisitor extends ClassVisitor {
    private final List<TimedMethodCandidate> timedMethods = new ArrayList<>();
    private String className;
    private int classAccess;

    private TimedClassVisitor() {
      super(Opcodes.ASM9);
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      className = name.replace('/', '.');
      classAccess = access;
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      return new TimedMethodVisitor(className, classAccess, access, name, descriptor, timedMethods);
    }

    private String className() {
      return className;
    }

    private List<TimedMethodCandidate> timedMethods() {
      return timedMethods;
    }
  }

  private static final class TimedMethodVisitor extends MethodVisitor {
    private final String className;
    private final int classAccess;
    private final int access;
    private final String methodName;
    private final String descriptor;
    private final List<TimedMethodCandidate> timedMethods;

    private TimedMethodVisitor(
        String className,
        int classAccess,
        int access,
        String methodName,
        String descriptor,
        List<TimedMethodCandidate> timedMethods) {
      super(Opcodes.ASM9);
      this.className = className;
      this.classAccess = classAccess;
      this.access = access;
      this.methodName = methodName;
      this.descriptor = descriptor;
      this.timedMethods = timedMethods;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
      if (!TIMED_DESCRIPTOR.equals(annotationDescriptor)) {
        return null;
      }
      return new TimedAnnotationVisitor(
          className, classAccess, access, methodName, descriptor, timedMethods);
    }
  }

  private static final class TimedAnnotationVisitor extends AnnotationVisitor {
    private final String className;
    private final int classAccess;
    private final int access;
    private final String methodName;
    private final String descriptor;
    private final List<TimedMethodCandidate> timedMethods;

    private TimedAnnotationVisitor(
        String className,
        int classAccess,
        int access,
        String methodName,
        String descriptor,
        List<TimedMethodCandidate> timedMethods) {
      super(Opcodes.ASM9);
      this.className = className;
      this.classAccess = classAccess;
      this.access = access;
      this.methodName = methodName;
      this.descriptor = descriptor;
      this.timedMethods = timedMethods;
    }

    @Override
    public void visitEnd() {
      if (isSyntheticOrBridge(access)) {
        return;
      }
      validateSupportedTimedMethod(className, classAccess, access, methodName, descriptor);
      timedMethods.add(
          new TimedMethodCandidate(methodName, descriptor, (access & Opcodes.ACC_STATIC) != 0));
    }
  }

  private static boolean isSyntheticOrBridge(int access) {
    return (access & Opcodes.ACC_SYNTHETIC) != 0 || (access & Opcodes.ACC_BRIDGE) != 0;
  }

  private static void validateSupportedTimedMethod(
      String className, int classAccess, int access, String methodName, String descriptor) {
    String methodDetails = methodDetails(className, methodName, descriptor);
    if ("<init>".equals(methodName)) {
      throw new IllegalArgumentException(
          "Instrumentation unsupported for "
              + methodDetails
              + ": @Timed cannot be applied to constructors because constructor bytecode has "
              + "special initialization rules. Annotate a normal method instead.");
    }
    if ("<clinit>".equals(methodName)) {
      throw new IllegalArgumentException(
          "Instrumentation unsupported for "
              + methodDetails
              + ": @Timed cannot be applied to class initialisers.");
    }
    if ((access & Opcodes.ACC_NATIVE) != 0) {
      throw new IllegalArgumentException(
          "Instrumentation unsupported for "
              + methodDetails
              + ": @Timed cannot be applied to native methods because there is no bytecode body "
              + "to instrument.");
    }
    if ((access & Opcodes.ACC_ABSTRACT) != 0) {
      throw new IllegalArgumentException(
          "Instrumentation unsupported for "
              + methodDetails
              + ": @Timed cannot be applied to abstract methods because there is no method body "
              + "to instrument. Annotate the concrete implementation instead.");
    }
    if ((classAccess & Opcodes.ACC_INTERFACE) != 0) {
      throw new IllegalArgumentException(
          "Instrumentation unsupported for "
              + methodDetails
              + ": @Timed default interface methods are not supported yet. Annotate a concrete "
              + "class method instead.");
    }
  }

  private static String methodDetails(String className, String methodName, String descriptor) {
    return className + LatencyClockedConstants.CLASS_NAME_SEPARATOR + methodName + descriptor;
  }
}
