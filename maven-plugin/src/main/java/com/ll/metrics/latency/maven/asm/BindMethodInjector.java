package com.ll.metrics.latency.maven.asm;

import com.ll.metrics.latency.maven.model.TimedMethodDescriptorEntry;
import java.util.Collection;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class BindMethodInjector {
  private BindMethodInjector() {}

  static void inject(
      ClassVisitor visitor,
      String internalClassName,
      Collection<TimedMethodDescriptorEntry> timedMethods) {
    MethodVisitor methodVisitor =
        visitor.visitMethod(
            Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
            AsmConstants.BIND_METHOD_NAME,
            AsmConstants.BIND_METHOD_DESCRIPTOR,
            null,
            null);
    methodVisitor.visitCode();
    for (TimedMethodDescriptorEntry timedMethod : timedMethods) {
      methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
      methodVisitor.visitLdcInsn(timedMethod.timerId());
      methodVisitor.visitMethodInsn(
          Opcodes.INVOKEINTERFACE,
          AsmConstants.TIMERS_INTERNAL_NAME,
          "claim",
          "(Ljava/lang/String;)" + AsmConstants.TIMER_DESCRIPTOR,
          true);
      methodVisitor.visitFieldInsn(
          Opcodes.PUTSTATIC,
          internalClassName,
          timedMethod.timerFieldName(),
          AsmConstants.TIMER_DESCRIPTOR);
    }
    methodVisitor.visitInsn(Opcodes.RETURN);
    methodVisitor.visitMaxs(0, 0);
    methodVisitor.visitEnd();
  }
}
