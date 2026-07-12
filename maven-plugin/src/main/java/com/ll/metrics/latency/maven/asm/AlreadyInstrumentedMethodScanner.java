package com.ll.metrics.latency.maven.asm;

import com.ll.metrics.latency.maven.model.TimedMethodDescriptorEntry;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Finds candidate timed methods that already contain generated timing bytecode so repeated plugin
 * runs can skip method body instrumentation safely.
 */
final class AlreadyInstrumentedMethodScanner extends ClassVisitor {
  private final Map<MethodKey, TimedMethodDescriptorEntry> candidateMethodsToInstrument;
  private final Set<MethodKey> instrumentedMethods = new HashSet<>();
  private String internalClassName;

  AlreadyInstrumentedMethodScanner(
      Map<MethodKey, TimedMethodDescriptorEntry> candidateMethodsToInstrument) {
    super(Opcodes.ASM9);
    this.candidateMethodsToInstrument = candidateMethodsToInstrument;
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
  }

  @Override
  public MethodVisitor visitMethod(
      int access, String name, String descriptor, String signature, String[] exceptions) {
    MethodKey methodKey = new MethodKey(name, descriptor);
    TimedMethodDescriptorEntry timedMethod = candidateMethodsToInstrument.get(methodKey);
    if (timedMethod == null) {
      return null;
    }
    String visitedClassName = Objects.requireNonNull(internalClassName, "internalClassName");
    return new MethodVisitor(Opcodes.ASM9) {
      private boolean sawEntryNanoTime;
      private boolean sawGeneratedTimerField;

      @Override
      public void visitMethodInsn(
          int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (opcode == Opcodes.INVOKESTATIC
            && "java/lang/System".equals(owner)
            && "nanoTime".equals(name)
            && "()J".equals(descriptor)) {
          sawEntryNanoTime = true;
        }
        if (sawGeneratedTimerField
            && opcode == Opcodes.INVOKEINTERFACE
            && AsmConstants.TIMER_INTERNAL_NAME.equals(owner)
            && "record".equals(name)
            && "(J)V".equals(descriptor)) {
          instrumentedMethods.add(methodKey);
          sawGeneratedTimerField = false;
        }
      }

      @Override
      public void visitVarInsn(int opcode, int variable) {
        if (sawEntryNanoTime && opcode == Opcodes.LSTORE) {
          instrumentedMethods.add(methodKey);
        }
        sawEntryNanoTime = false;
      }

      @Override
      public void visitFieldInsn(int opcode, String owner, String fieldName, String descriptor) {
        if (opcode == Opcodes.GETSTATIC
            && visitedClassName.equals(owner)
            && timedMethod.fieldName().equals(fieldName)
            && AsmConstants.TIMER_DESCRIPTOR.equals(descriptor)) {
          sawGeneratedTimerField = true;
        }
      }
    };
  }

  Set<MethodKey> instrumentedMethods() {
    return instrumentedMethods;
  }
}
