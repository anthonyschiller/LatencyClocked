package com.ll.metrics.latency.maven.asm;

import com.ll.metrics.latency.maven.model.LatencyDescriptorEntry;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

final class TimedMethodInstrumenter extends AdviceAdapter {
  private final String internalClassName;
  private final LatencyDescriptorEntry entry;
  private int startLocal;

  TimedMethodInstrumenter(
      MethodVisitor delegate,
      int access,
      String name,
      String descriptor,
      String internalClassName,
      LatencyDescriptorEntry entry) {
    super(Opcodes.ASM9, delegate, access, name, descriptor);
    this.internalClassName = internalClassName;
    this.entry = entry;
  }

  @Override
  protected void onMethodEnter() {
    startLocal = newLocal(Type.LONG_TYPE);
    push(0L);
    storeLocal(startLocal, Type.LONG_TYPE);
    Label skip = newLabel();
    invokeStatic(AsmConstants.LATENCY_CLOCKED_TYPE, AsmConstants.ENABLED);
    visitJumpInsn(Opcodes.IFEQ, skip);
    invokeStatic(AsmConstants.SYSTEM_TYPE, AsmConstants.NANO_TIME);
    storeLocal(startLocal, Type.LONG_TYPE);
    mark(skip);
  }

  @Override
  protected void onMethodExit(int opcode) {
    if (opcode == ATHROW) {
      return;
    }
    final Label skip = newLabel();
    invokeStatic(AsmConstants.LATENCY_CLOCKED_TYPE, AsmConstants.ENABLED);
    visitJumpInsn(Opcodes.IFEQ, skip);
    visitFieldInsn(
        Opcodes.GETSTATIC, internalClassName, entry.fieldName(), AsmConstants.TIMER_DESCRIPTOR);
    invokeStatic(AsmConstants.SYSTEM_TYPE, AsmConstants.NANO_TIME);
    loadLocal(startLocal, Type.LONG_TYPE);
    math(SUB, Type.LONG_TYPE);
    invokeInterface(AsmConstants.TIMER_TYPE, AsmConstants.RECORD);
    mark(skip);
  }
}
