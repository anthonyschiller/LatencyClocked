package com.ll.metrics.latency.maven.asm;

import com.ll.metrics.latency.maven.model.TimedMethodDescriptorEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Injects generated timer fields and startup bind methods into compiled classes. */
public final class TimerFieldInjector {
  /** Injects generated members and instruments timed method bodies in the supplied class file. */
  public FieldInjectionResult inject(
      Path classFile, Collection<TimedMethodDescriptorEntry> timedMethods) throws IOException {
    Objects.requireNonNull(classFile, "classFile");
    Objects.requireNonNull(timedMethods, "timedMethods");

    byte[] classBytes = Files.readAllBytes(classFile);
    ClassReader reader = new ClassReader(classBytes);
    Map<MethodKey, TimedMethodDescriptorEntry> candidateMethodsToInstrument =
        timedMethodsByMethod(timedMethods);
    AlreadyInstrumentedMethodScanner scanner =
        new AlreadyInstrumentedMethodScanner(candidateMethodsToInstrument);
    reader.accept(scanner, ClassReader.SKIP_DEBUG);

    ClassWriter writer =
        new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    TimerFieldClassVisitor injector =
        new TimerFieldClassVisitor(
            writer, timedMethods, candidateMethodsToInstrument, scanner.instrumentedMethods());
    reader.accept(injector, ClassReader.EXPAND_FRAMES);
    if (injector.changed()) {
      Files.write(classFile, writer.toByteArray());
    }
    return new FieldInjectionResult(
        injector.injectedFields(),
        injector.skippedFields(),
        injector.injectedBindMethods(),
        injector.skippedBindMethods(),
        injector.instrumentedMethods(),
        injector.skippedInstrumentedMethods());
  }

  /** Counts members injected and skipped because they already existed. */
  public record FieldInjectionResult(
      int injectedFields,
      int skippedFields,
      int injectedBindMethods,
      int skippedBindMethods,
      int instrumentedMethods,
      int skippedInstrumentedMethods) {}

  private static final class TimerFieldClassVisitor extends ClassVisitor {
    private final List<TimedMethodDescriptorEntry> timedMethods;
    private final Map<MethodKey, TimedMethodDescriptorEntry> timedMethodsByMethod;
    private final Set<MethodKey> alreadyInstrumentedMethods;
    private final Set<String> requiredFields;
    private final Set<String> existingFields = new HashSet<>();
    private String internalClassName;
    private int injectedFields;
    private int instrumentedMethods;
    private int skippedInstrumentedMethods;
    private boolean existingBindMethod;
    private boolean injectedBindMethod;

    private TimerFieldClassVisitor(
        ClassVisitor delegate,
        Collection<TimedMethodDescriptorEntry> timedMethods,
        Map<MethodKey, TimedMethodDescriptorEntry> timedMethodsByMethod,
        Set<MethodKey> alreadyInstrumentedMethods) {
      super(Opcodes.ASM9, delegate);
      this.timedMethods = List.copyOf(timedMethods);
      this.timedMethodsByMethod = Map.copyOf(timedMethodsByMethod);
      this.alreadyInstrumentedMethods = Set.copyOf(alreadyInstrumentedMethods);
      requiredFields =
          timedMethods.stream()
              .map(TimedMethodDescriptorEntry::timerFieldName)
              .collect(Collectors.toSet());
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      internalClassName = name;
      super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(
        int access, String name, String descriptor, String signature, Object value) {
      existingFields.add(name);
      return super.visitField(access, name, descriptor, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      if (AsmConstants.BIND_METHOD_NAME.equals(name)
          && AsmConstants.BIND_METHOD_DESCRIPTOR.equals(descriptor)) {
        existingBindMethod = true;
      }
      MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
      MethodKey methodKey = new MethodKey(name, descriptor);
      TimedMethodDescriptorEntry timedMethod = timedMethodsByMethod.get(methodKey);
      if (timedMethod == null || !isInstrumentable(access, name)) {
        return delegate;
      }
      if (alreadyInstrumentedMethods.contains(methodKey)) {
        skippedInstrumentedMethods++;
        return delegate;
      }
      instrumentedMethods++;
      return new TimedMethodInstrumenter(
          delegate, access, name, descriptor, internalClassName, timedMethod);
    }

    @Override
    public void visitEnd() {
      for (TimedMethodDescriptorEntry timedMethod : timedMethods) {
        if (existingFields.contains(timedMethod.timerFieldName())) {
          continue;
        }
        FieldVisitor fieldVisitor =
            super.visitField(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                timedMethod.timerFieldName(),
                AsmConstants.TIMER_DESCRIPTOR,
                null,
                null);
        if (fieldVisitor != null) {
          fieldVisitor.visitEnd();
        }
        injectedFields++;
        existingFields.add(timedMethod.timerFieldName());
      }
      if (!existingBindMethod) {
        BindMethodInjector.inject(cv, internalClassName, timedMethods);
        injectedBindMethod = true;
      }
      super.visitEnd();
    }

    private int injectedFields() {
      return injectedFields;
    }

    private int skippedFields() {
      int skippedFields = 0;
      for (String fieldName : requiredFields) {
        if (existingFields.contains(fieldName)) {
          skippedFields++;
        }
      }
      return skippedFields - injectedFields;
    }

    private int injectedBindMethods() {
      return injectedBindMethod ? 1 : 0;
    }

    private int skippedBindMethods() {
      return existingBindMethod ? 1 : 0;
    }

    private int instrumentedMethods() {
      return instrumentedMethods;
    }

    private int skippedInstrumentedMethods() {
      return skippedInstrumentedMethods;
    }

    private boolean changed() {
      return injectedFields > 0 || injectedBindMethod || instrumentedMethods > 0;
    }
  }

  private static Map<MethodKey, TimedMethodDescriptorEntry> timedMethodsByMethod(
      Collection<TimedMethodDescriptorEntry> timedMethods) {
    Map<MethodKey, TimedMethodDescriptorEntry> timedMethodsByMethod = new HashMap<>();
    for (TimedMethodDescriptorEntry timedMethod : timedMethods) {
      timedMethodsByMethod.put(
          new MethodKey(timedMethod.methodName(), timedMethod.methodDescriptor()), timedMethod);
    }
    return timedMethodsByMethod;
  }

  private static boolean isInstrumentable(int access, String methodName) {
    return !"<init>".equals(methodName)
        && !"<clinit>".equals(methodName)
        && (access & Opcodes.ACC_SYNTHETIC) == 0
        && (access & Opcodes.ACC_BRIDGE) == 0;
  }
}
