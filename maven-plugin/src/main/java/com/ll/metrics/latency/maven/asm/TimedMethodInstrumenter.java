package com.ll.metrics.latency.maven.asm;

import com.ll.metrics.latency.maven.model.LatencyDescriptorEntry;
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
    invokeStatic(AsmConstants.SYSTEM_TYPE, AsmConstants.NANO_TIME);
    startLocal = newLocal(Type.LONG_TYPE);
    storeLocal(startLocal, Type.LONG_TYPE);
  }

  @Override
  protected void onMethodExit(int opcode) {
    if (opcode == ATHROW) {
      return;
    }
    visitFieldInsn(
        Opcodes.GETSTATIC, internalClassName, entry.fieldName(), AsmConstants.TIMER_DESCRIPTOR);
    invokeStatic(AsmConstants.SYSTEM_TYPE, AsmConstants.NANO_TIME);
    loadLocal(startLocal, Type.LONG_TYPE);
    math(SUB, Type.LONG_TYPE);
    invokeInterface(AsmConstants.TIMER_TYPE, AsmConstants.RECORD);
  }
}
