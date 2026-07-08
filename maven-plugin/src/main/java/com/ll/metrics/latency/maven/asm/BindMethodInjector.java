package com.ll.metrics.latency.maven.asm;

import com.ll.metrics.latency.maven.model.LatencyDescriptorEntry;
import java.util.Collection;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class BindMethodInjector {
  private BindMethodInjector() {}

  static void inject(
      ClassVisitor visitor, String internalClassName, Collection<LatencyDescriptorEntry> entries) {
    MethodVisitor methodVisitor =
        visitor.visitMethod(
            Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
            AsmConstants.BIND_METHOD_NAME,
            AsmConstants.BIND_METHOD_DESCRIPTOR,
            null,
            null);
    methodVisitor.visitCode();
    for (LatencyDescriptorEntry entry : entries) {
      methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
      methodVisitor.visitLdcInsn(entry.timerId());
      methodVisitor.visitMethodInsn(
          Opcodes.INVOKEINTERFACE,
          AsmConstants.TIMERS_INTERNAL_NAME,
          "timer",
          "(Ljava/lang/String;)" + AsmConstants.TIMER_DESCRIPTOR,
          true);
      methodVisitor.visitFieldInsn(
          Opcodes.PUTSTATIC, internalClassName, entry.fieldName(), AsmConstants.TIMER_DESCRIPTOR);
    }
    methodVisitor.visitInsn(Opcodes.RETURN);
    methodVisitor.visitMaxs(0, 0);
    methodVisitor.visitEnd();
  }
}
