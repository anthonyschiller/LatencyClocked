package com.ll.metrics.latency.maven.asm;

import com.ll.metrics.latency.maven.model.TimedMethodDescriptorEntry;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

final class TimedMethodInstrumenter extends AdviceAdapter {
  private final String internalClassName;
  private final TimedMethodDescriptorEntry timedMethod;
  private int enabledLocal;
  private int startLocal;

  TimedMethodInstrumenter(
      MethodVisitor delegate,
      int access,
      String name,
      String descriptor,
      String internalClassName,
      TimedMethodDescriptorEntry timedMethod) {
    super(Opcodes.ASM9, delegate, access, name, descriptor);
    this.internalClassName = internalClassName;
    this.timedMethod = timedMethod;
  }

  @Override
  protected void onMethodEnter() {
    enabledLocal = newLocal(Type.BOOLEAN_TYPE);
    startLocal = newLocal(Type.LONG_TYPE);

    invokeStatic(AsmConstants.LATENCY_CLOCKED_TYPE, AsmConstants.ENABLED);
    storeLocal(enabledLocal, Type.BOOLEAN_TYPE);

    push(0L);
    storeLocal(startLocal, Type.LONG_TYPE);

    Label skip = newLabel();
    loadLocal(enabledLocal, Type.BOOLEAN_TYPE);
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
    loadLocal(enabledLocal, Type.BOOLEAN_TYPE);
    visitJumpInsn(Opcodes.IFEQ, skip);
    invokeStatic(AsmConstants.LATENCY_CLOCKED_TYPE, AsmConstants.ENABLED);
    visitJumpInsn(Opcodes.IFEQ, skip);
    visitFieldInsn(
        Opcodes.GETSTATIC,
        internalClassName,
        timedMethod.fieldName(),
        AsmConstants.TIMER_DESCRIPTOR);
    ensureTimerIsBound();
    invokeStatic(AsmConstants.SYSTEM_TYPE, AsmConstants.NANO_TIME);
    loadLocal(startLocal, Type.LONG_TYPE);
    math(SUB, Type.LONG_TYPE);
    invokeInterface(AsmConstants.TIMER_TYPE, AsmConstants.RECORD);
    mark(skip);
  }

  private void ensureTimerIsBound() {
    Label bound = newLabel();
    dup();
    visitJumpInsn(Opcodes.IFNONNULL, bound);
    pop();
    throwException(Type.getType(IllegalStateException.class), uninitialisedTimerMessage());
    mark(bound);
  }

  private String uninitialisedTimerMessage() {
    return "LatencyClocked timer field "
        + timedMethod.fieldName()
        + " for "
        + timedMethod.className()
        + "#"
        + timedMethod.methodName()
        + timedMethod.methodDescriptor()
        + " is not bound. Call LatencyClocked.initialise(...) before invoking @Timed methods "
        + "and ensure latency-clocked:instrument runs for this module.";
  }
}
