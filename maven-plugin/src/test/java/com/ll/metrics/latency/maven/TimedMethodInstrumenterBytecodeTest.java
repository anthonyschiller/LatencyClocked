package com.ll.metrics.latency.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ll.metrics.latency.constants.LatencyClockedConstants;
import com.ll.metrics.latency.maven.asm.AsmConstants;
import com.ll.metrics.latency.maven.model.TimedMethodDescriptorEntry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

class TimedMethodInstrumenterBytecodeTest {
  private static final String SAMPLE_CLASS_NAME = "golden.GoldenTimedSamples";
  private static final String SAMPLE_CLASS_RESOURCE =
      "golden/GoldenTimedSamples" + LatencyClockedConstants.CLASS_FILE_EXTENSION;
  private static final String LATENCY_CLOCKED_OWNER =
      "com/ll/metrics/latency/core/LatencyClocked";
  private static final String SYSTEM_OWNER = "java/lang/System";
  private static final String TIMER_OWNER = AsmConstants.TIMER_INTERNAL_NAME;
  private static final String TIMERS_OWNER = AsmConstants.TIMERS_INTERNAL_NAME;
  private static final String TIMER_DESCRIPTOR = AsmConstants.TIMER_DESCRIPTOR;

  @Test
  void instrumentationCodeSkippedWhenLatencyClockedDisabled(@TempDir Path outputDirectory)
      throws Exception {
    InstrumentedSample sample = instrumentedSample(outputDirectory);
    MethodNode method = sample.method("successfulVoid", "()V");
    String fieldName = sample.timerFieldName("successfulVoid", "()V");

    InstrumentationShape shape = assertTimedInstrumentationContract(method, fieldName);

    assertEquals(shape.enabledLocal(), shape.exitEntryEnabledLoad().var);
    assertTrue(
        indexOf(method, shape.entryEnabledSkipTarget()) > indexOf(method, shape.entryNano()));
    assertTrue(
        indexOf(method, shape.exitEntryEnabledSkipTarget()) > indexOf(method, shape.timerRecord()));
    assertTrue(
        indexOf(method, shape.exitCurrentEnabledSkipTarget())
            > indexOf(method, shape.timerRecord()));
  }

  @Test
  void noTimerFieldAccessAndBoundCheckWhenLatencyClockedDisabled(@TempDir Path outputDirectory)
      throws Exception {
    InstrumentedSample sample = instrumentedSample(outputDirectory);
    MethodNode method = sample.method("successfulVoid", "()V");

    InstrumentationShape shape =
        assertTimedInstrumentationContract(method, sample.timerFieldName("successfulVoid", "()V"));

    assertTrue(
        indexOf(method, shape.exitEntryEnabledLoad()) < indexOf(method, shape.timerFieldLoad()));
    assertTrue(
        indexOf(method, shape.exitCurrentEnabled()) < indexOf(method, shape.timerFieldLoad()));
    assertTrue(indexOf(method, shape.timerFieldLoad()) < indexOf(method, shape.timerBoundCheck()));
    assertTrue(indexOf(method, shape.timerBoundCheck()) < indexOf(method, shape.timerRecord()));
    assertTrue(indexOf(method, shape.unboundException()) < indexOf(method, shape.timerRecord()));
  }

  @Test
  void operationalSequenceToRecordElapsedDurationToTimer(
      @TempDir Path outputDirectory) throws Exception {
    InstrumentedSample sample = instrumentedSample(outputDirectory);
    MethodNode method = sample.method("successfulVoid", "()V");

    InstrumentationShape shape =
        assertTimedInstrumentationContract(method, sample.timerFieldName("successfulVoid", "()V"));

    assertEquals(Opcodes.INVOKEINTERFACE, shape.timerRecord().getOpcode());
    assertEquals(TIMER_OWNER, shape.timerRecord().owner);
    assertEquals("record", shape.timerRecord().name);
    assertEquals("(J)V", shape.timerRecord().desc);
    assertEquals(shape.startLocal(), shape.exitStartLoad().var);
    assertTrue(indexOf(method, shape.exitNano()) < indexOf(method, shape.exitStartLoad()));
    assertTrue(indexOf(method, shape.exitStartLoad()) < indexOf(method, shape.subtract()));
    assertTrue(indexOf(method, shape.subtract()) < indexOf(method, shape.timerRecord()));
  }

  @Test
  void allJvmSuccessfulReturnCategoriesAreInstrumented(@TempDir Path outputDirectory)
      throws Exception {
    InstrumentedSample sample = instrumentedSample(outputDirectory);

    assertSingleReturnOpcodeIsInstrumented(sample, "successfulVoid", "()V", Opcodes.RETURN);
    assertSingleReturnOpcodeIsInstrumented(sample, "primitiveReturn", "(I)I", Opcodes.IRETURN);
    assertSingleReturnOpcodeIsInstrumented(sample, "longReturn", "(J)J", Opcodes.LRETURN);
    assertSingleReturnOpcodeIsInstrumented(sample, "floatReturn", "(F)F", Opcodes.FRETURN);
    assertSingleReturnOpcodeIsInstrumented(sample, "doubleReturn", "(D)D", Opcodes.DRETURN);
    assertSingleReturnOpcodeIsInstrumented(
        sample, "objectReturn", "(Ljava/lang/String;)Ljava/lang/String;", Opcodes.ARETURN);
  }

  @Test
  void withMultipleSuccessfulReturnPathsEachRecordsExactlyOnce(@TempDir Path outputDirectory)
      throws Exception {
    InstrumentedSample sample = instrumentedSample(outputDirectory);
    MethodNode method = sample.method("multipleReturnPaths", "(I)I");

    assertTimedInstrumentationContract(
        method, sample.timerFieldName("multipleReturnPaths", "(I)I"));
    assertEquals(3, successfulReturnCount(method));
    assertEquals(3, recordCallCount(method));
    assertEverySuccessfulReturnHasOnePrecedingRecord(method);
  }

  @Test
  void noTimeRecordingRunsForMethodThatCanOnlyExitExceptionally(
      @TempDir Path outputDirectory) throws Exception {
    InstrumentedSample sample = instrumentedSample(outputDirectory);
    MethodNode method = sample.method("throwingMethod", "()V");

    assertTrue(containsOpcode(method, Opcodes.ATHROW));
    assertEquals(0, recordCallCount(method));
    assertFalse(containsTimerFieldLoad(method));
    assertEquals(1, nanoTimeCallCount(method));
  }

  @Test
  void timeRecordingAppliesForOnlySuccessfulReturnPaths(
          @TempDir Path outputDirectory) throws Exception {
    InstrumentedSample sample = instrumentedSample(outputDirectory);
    MethodNode method = sample.method("maybeThrow", "(Z)I");

    assertTimedInstrumentationContract(method, sample.timerFieldName("maybeThrow", "(Z)I"));
    assertTrue(containsOpcode(method, Opcodes.ATHROW));
    assertEquals(1, successfulReturnCount(method));
    assertEquals(1, recordCallCount(method));
    assertEverySuccessfulReturnHasOnePrecedingRecord(method);
  }

  @Test
  void timeRecordingAppliesForSuccessfulReturnPathsBeforeTerminalThrow(
      @TempDir Path outputDirectory)
      throws Exception {
    InstrumentedSample sample = instrumentedSample(outputDirectory);
    MethodNode method = sample.method("exceptionThrownIfValueUnhandled", "(I)I");

    assertTimedInstrumentationContract(
        method, sample.timerFieldName("exceptionThrownIfValueUnhandled", "(I)I"));
    assertTrue(containsOpcode(method, Opcodes.ATHROW));
    assertEquals(2, successfulReturnCount(method));
    assertEquals(2, recordCallCount(method));
    assertEverySuccessfulReturnHasOnePrecedingRecord(method);
  }

  @Test
  void internallyHandledExceptionStillRecordForSuccessfulReturnPaths(@TempDir Path outputDirectory)
      throws Exception {
    InstrumentedSample sample = instrumentedSample(outputDirectory);
    MethodNode method = sample.method("catchesInternally", "(Z)I");

    assertTimedInstrumentationContract(method, sample.timerFieldName("catchesInternally", "(Z)I"));
    assertTrue(containsOpcode(method, Opcodes.ATHROW));
    assertEquals(2, successfulReturnCount(method));
    assertEquals(2, recordCallCount(method));
    assertEverySuccessfulReturnHasOnePrecedingRecord(method);
  }

  @Test
  void eachTimedMethodUsesItsOwnGeneratedTimerField(@TempDir Path outputDirectory)
      throws Exception {
    InstrumentedSample sample = instrumentedSample(outputDirectory);
    MethodNode methodA = sample.method("successfulVoid", "()V");
    MethodNode methodB = sample.method("primitiveReturn", "(I)I");
    String timerFieldToMethodA = sample.timerFieldName("successfulVoid", "()V");
    String timerFieldToMethodB = sample.timerFieldName("primitiveReturn", "(I)I");

    assertNotEquals(timerFieldToMethodA, timerFieldToMethodB);
    assertTimedInstrumentationContract(methodA, timerFieldToMethodA);
    assertTimedInstrumentationContract(methodB, timerFieldToMethodB);
    assertFalse(containsTimerFieldLoad(methodA, timerFieldToMethodB));
    assertFalse(containsTimerFieldLoad(methodB, timerFieldToMethodA));
    assertFalse(calls(methodA, TIMERS_OWNER, "claim", "(Ljava/lang/String;)" + TIMER_DESCRIPTOR));
    assertFalse(calls(methodB, TIMERS_OWNER, "claim", "(Ljava/lang/String;)" + TIMER_DESCRIPTOR));
  }

  private static void assertSingleReturnOpcodeIsInstrumented(
      InstrumentedSample sample, String methodName, String descriptor, int expectedReturnOpcode) {
    MethodNode method = sample.method(methodName, descriptor);

    assertTrue(containsOpcode(method, expectedReturnOpcode));
    assertEquals(1, successfulReturnCount(method));
    assertEquals(1, recordCallCount(method));
    assertTimedInstrumentationContract(method, sample.timerFieldName(methodName, descriptor));
    assertEverySuccessfulReturnHasOnePrecedingRecord(method);
  }

  private static InstrumentationShape assertTimedInstrumentationContract(
      MethodNode method, String expectedTimerField) {
    final MethodInsnNode entryEnabled =
        findMethodCall(method, LATENCY_CLOCKED_OWNER, "enabled", "()Z", 0);
    final VarInsnNode entryEnabledStore =
        assertVarInstruction(nextMeaningful(entryEnabled), Opcodes.ISTORE);
    final int enabledLocal = entryEnabledStore.var;
    final VarInsnNode entryEnabledLoad =
        findVarInstructionAfter(method, entryEnabledStore, Opcodes.ILOAD, enabledLocal);
    final JumpInsnNode entryEnabledJump =
        assertJumpInstruction(nextMeaningful(entryEnabledLoad), Opcodes.IFEQ);
    final MethodInsnNode entryNano =
        findMethodCallAfter(method, entryEnabledJump, SYSTEM_OWNER, "nanoTime", "()J");
    final VarInsnNode startStore = assertVarInstruction(nextMeaningful(entryNano), Opcodes.LSTORE);
    final int startLocal = startStore.var;
    final MethodInsnNode timerRecord =
        findMethodCall(method, TIMER_OWNER, "record", "(J)V", 0);
    final FieldInsnNode timerFieldLoad =
        findTimerFieldLoadBefore(method, timerRecord, expectedTimerField);
    final MethodInsnNode exitCurrentEnabled =
        findMethodCallBefore(method, timerFieldLoad, LATENCY_CLOCKED_OWNER, "enabled", "()Z");
    final JumpInsnNode exitCurrentEnabledJump =
        assertJumpInstruction(nextMeaningful(exitCurrentEnabled), Opcodes.IFEQ);
    final VarInsnNode exitEntryEnabledLoad =
        findVarInstructionBefore(method, exitCurrentEnabled, Opcodes.ILOAD, enabledLocal);
    final JumpInsnNode exitEntryEnabledJump =
        assertJumpInstruction(nextMeaningful(exitEntryEnabledLoad), Opcodes.IFEQ);
    final JumpInsnNode timerBoundCheck = findJumpAfter(method, timerFieldLoad, Opcodes.IFNONNULL);
    final AbstractInsnNode unboundException =
        findOpcodeAfter(method, timerFieldLoad, Opcodes.ATHROW);
    final MethodInsnNode exitNano =
        findMethodCallBefore(method, timerRecord, SYSTEM_OWNER, "nanoTime", "()J");
    final VarInsnNode exitStartLoad =
        findVarInstructionBefore(method, timerRecord, Opcodes.LLOAD, startLocal);
    final AbstractInsnNode subtract = findOpcodeBefore(method, timerRecord, Opcodes.LSUB);

    assertEquals(Opcodes.INVOKESTATIC, entryEnabled.getOpcode());
    assertEquals(Opcodes.INVOKESTATIC, exitCurrentEnabled.getOpcode());
    assertEquals(Opcodes.INVOKESTATIC, entryNano.getOpcode());
    assertEquals(Opcodes.INVOKESTATIC, exitNano.getOpcode());
    assertTrue(indexOf(method, entryEnabled) < indexOf(method, entryEnabledStore));
    assertTrue(indexOf(method, entryEnabledLoad) < indexOf(method, entryNano));
    assertTrue(indexOf(method, exitEntryEnabledLoad) < indexOf(method, exitCurrentEnabled));
    assertTrue(indexOf(method, exitCurrentEnabled) < indexOf(method, timerFieldLoad));
    assertTrue(indexOf(method, timerFieldLoad) < indexOf(method, exitNano));
    assertTrue(indexOf(method, exitNano) < indexOf(method, timerRecord));
    assertFalse(usesStartLocalAsSentinel(method, startLocal, timerFieldLoad));

    return new InstrumentationShape(
        enabledLocal,
        startLocal,
        entryEnabled,
        entryEnabledLoad,
        entryEnabledJump,
        entryNano,
        targetMeaningful(entryEnabledJump),
        exitEntryEnabledLoad,
        exitEntryEnabledJump,
        targetMeaningful(exitEntryEnabledJump),
        exitCurrentEnabled,
        exitCurrentEnabledJump,
        targetMeaningful(exitCurrentEnabledJump),
        timerFieldLoad,
        timerBoundCheck,
        unboundException,
        exitNano,
        exitStartLoad,
        subtract,
        timerRecord);
  }

  private static void assertEverySuccessfulReturnHasOnePrecedingRecord(MethodNode method) {
    int recordsSinceLastExit = 0;
    for (AbstractInsnNode instruction : method.instructions) {
      if (!isMeaningful(instruction)) {
        continue;
      }
      if (isTimerRecord(instruction)) {
        recordsSinceLastExit++;
      } else if (isSuccessfulReturn(instruction.getOpcode())) {
        assertEquals(1, recordsSinceLastExit);
        recordsSinceLastExit = 0;
      } else if (instruction.getOpcode() == Opcodes.ATHROW) {
        assertEquals(0, recordsSinceLastExit);
      }
    }
  }

  private static InstrumentedSample instrumentedSample(Path outputDirectory) throws IOException {
    GoldenFixtureCompiler.compile(outputDirectory, "GoldenTimedSamples.java");
    Map<Path, List<TimedMethodDescriptorEntry>> timedMethodsByClassFile =
        LatencyClockedInstrumenter.scan(outputDirectory);
    LatencyClockedInstrumenter.instrument(timedMethodsByClassFile);
    Path classFile = outputDirectory.resolve(SAMPLE_CLASS_RESOURCE);
    ClassNode classNode = readClassNode(classFile);
    Map<MethodKey, TimedMethodDescriptorEntry> timedMethods = new HashMap<>();
    timedMethodsByClassFile.values().stream()
        .flatMap(List::stream)
        .forEach(
            timedMethod ->
                timedMethods.put(
                    new MethodKey(timedMethod.methodName(), timedMethod.methodDescriptor()),
                    timedMethod));
    return new InstrumentedSample(classNode, timedMethods);
  }

  private static ClassNode readClassNode(Path classFile) throws IOException {
    ClassNode classNode = new ClassNode();
    try (InputStream inputStream = Files.newInputStream(classFile)) {
      new ClassReader(inputStream).accept(classNode, 0);
    }
    return classNode;
  }

  private static MethodNode findMethod(
      ClassNode classNode, String methodName, String descriptor) {
    return classNode.methods.stream()
        .filter(method -> methodName.equals(method.name) && descriptor.equals(method.desc))
        .findFirst()
        .orElseThrow();
  }

  private static boolean isSuccessfulReturn(int opcode) {
    return switch (opcode) {
      case Opcodes.RETURN,
          Opcodes.IRETURN,
          Opcodes.LRETURN,
          Opcodes.FRETURN,
          Opcodes.DRETURN,
          Opcodes.ARETURN -> true;
      default -> false;
    };
  }

  private static int successfulReturnCount(MethodNode method) {
    int count = 0;
    for (AbstractInsnNode instruction : method.instructions) {
      if (isSuccessfulReturn(instruction.getOpcode())) {
        count++;
      }
    }
    return count;
  }

  private static int recordCallCount(MethodNode method) {
    int count = 0;
    for (AbstractInsnNode instruction : method.instructions) {
      if (isTimerRecord(instruction)) {
        count++;
      }
    }
    return count;
  }

  private static int nanoTimeCallCount(MethodNode method) {
    int count = 0;
    for (AbstractInsnNode instruction : method.instructions) {
      if (isMethodCall(instruction, SYSTEM_OWNER, "nanoTime", "()J")) {
        count++;
      }
    }
    return count;
  }

  private static MethodInsnNode findMethodCall(
      MethodNode method, String owner, String name, String descriptor, int occurrence) {
    int found = 0;
    for (AbstractInsnNode instruction : method.instructions) {
      if (isMethodCall(instruction, owner, name, descriptor)) {
        if (found == occurrence) {
          return (MethodInsnNode) instruction;
        }
        found++;
      }
    }
    throw new AssertionError("Missing method call " + owner + "." + name + descriptor);
  }

  private static MethodInsnNode findMethodCallAfter(
      MethodNode method,
      AbstractInsnNode after,
      String owner,
      String name,
      String descriptor) {
    for (AbstractInsnNode instruction = after.getNext();
        instruction != null;
        instruction = instruction.getNext()) {
      if (isMethodCall(instruction, owner, name, descriptor)) {
        return (MethodInsnNode) instruction;
      }
    }
    throw new AssertionError("Missing method call after marker");
  }

  private static MethodInsnNode findMethodCallBefore(
      MethodNode method,
      AbstractInsnNode before,
      String owner,
      String name,
      String descriptor) {
    for (AbstractInsnNode instruction = before.getPrevious();
        instruction != null;
        instruction = instruction.getPrevious()) {
      if (isMethodCall(instruction, owner, name, descriptor)) {
        return (MethodInsnNode) instruction;
      }
    }
    throw new AssertionError("Missing method call before marker");
  }

  private static VarInsnNode findVarInstructionAfter(
      MethodNode method, AbstractInsnNode after, int opcode, int variable) {
    for (AbstractInsnNode instruction = after.getNext();
        instruction != null;
        instruction = instruction.getNext()) {
      if (isVarInstruction(instruction, opcode, variable)) {
        return (VarInsnNode) instruction;
      }
    }
    throw new AssertionError("Missing variable instruction after marker");
  }

  private static VarInsnNode findVarInstructionBefore(
      MethodNode method, AbstractInsnNode before, int opcode, int variable) {
    for (AbstractInsnNode instruction = before.getPrevious();
        instruction != null;
        instruction = instruction.getPrevious()) {
      if (isVarInstruction(instruction, opcode, variable)) {
        return (VarInsnNode) instruction;
      }
    }
    throw new AssertionError("Missing variable instruction before marker");
  }

  private static FieldInsnNode findTimerFieldLoadBefore(
      MethodNode method, AbstractInsnNode before, String fieldName) {
    for (AbstractInsnNode instruction = before.getPrevious();
        instruction != null;
        instruction = instruction.getPrevious()) {
      if (isTimerFieldLoad(instruction, fieldName)) {
        return (FieldInsnNode) instruction;
      }
    }
    throw new AssertionError("Missing generated timer field load " + fieldName);
  }

  private static JumpInsnNode findJumpAfter(
      MethodNode method, AbstractInsnNode after, int opcode) {
    for (AbstractInsnNode instruction = after.getNext();
        instruction != null;
        instruction = instruction.getNext()) {
      if (instruction.getOpcode() == opcode && instruction instanceof JumpInsnNode jump) {
        return jump;
      }
    }
    throw new AssertionError("Missing jump instruction after marker");
  }

  private static AbstractInsnNode findOpcodeAfter(
      MethodNode method, AbstractInsnNode after, int opcode) {
    for (AbstractInsnNode instruction = after.getNext();
        instruction != null;
        instruction = instruction.getNext()) {
      if (instruction.getOpcode() == opcode) {
        return instruction;
      }
    }
    throw new AssertionError("Missing opcode after marker");
  }

  private static AbstractInsnNode findOpcodeBefore(
      MethodNode method, AbstractInsnNode before, int opcode) {
    for (AbstractInsnNode instruction = before.getPrevious();
        instruction != null;
        instruction = instruction.getPrevious()) {
      if (instruction.getOpcode() == opcode) {
        return instruction;
      }
    }
    throw new AssertionError("Missing opcode before marker");
  }

  private static VarInsnNode assertVarInstruction(AbstractInsnNode instruction, int opcode) {
    assertNotNull(instruction);
    assertEquals(opcode, instruction.getOpcode());
    assertTrue(instruction instanceof VarInsnNode);
    return (VarInsnNode) instruction;
  }

  private static JumpInsnNode assertJumpInstruction(AbstractInsnNode instruction, int opcode) {
    assertNotNull(instruction);
    assertEquals(opcode, instruction.getOpcode());
    assertTrue(instruction instanceof JumpInsnNode);
    return (JumpInsnNode) instruction;
  }

  private static AbstractInsnNode nextMeaningful(AbstractInsnNode instruction) {
    for (AbstractInsnNode current = instruction.getNext();
        current != null;
        current = current.getNext()) {
      if (isMeaningful(current)) {
        return current;
      }
    }
    return null;
  }

  private static AbstractInsnNode targetMeaningful(JumpInsnNode instruction) {
    return nextMeaningful(instruction.label);
  }

  private static boolean isMeaningful(AbstractInsnNode instruction) {
    return !(instruction instanceof LabelNode
        || instruction instanceof FrameNode
        || instruction instanceof LineNumberNode);
  }

  private static boolean containsOpcode(MethodNode method, int opcode) {
    for (AbstractInsnNode instruction : method.instructions) {
      if (instruction.getOpcode() == opcode) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsTimerFieldLoad(MethodNode method) {
    for (AbstractInsnNode instruction : method.instructions) {
      if (isTimerFieldLoad(instruction)) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsTimerFieldLoad(MethodNode method, String fieldName) {
    for (AbstractInsnNode instruction : method.instructions) {
      if (isTimerFieldLoad(instruction, fieldName)) {
        return true;
      }
    }
    return false;
  }

  private static boolean calls(
      MethodNode method, String owner, String name, String descriptor) {
    for (AbstractInsnNode instruction : method.instructions) {
      if (isMethodCall(instruction, owner, name, descriptor)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isTimerRecord(AbstractInsnNode instruction) {
    return isMethodCall(instruction, TIMER_OWNER, "record", "(J)V");
  }

  private static boolean isMethodCall(
      AbstractInsnNode instruction, String owner, String name, String descriptor) {
    if (!(instruction instanceof MethodInsnNode methodCall)) {
      return false;
    }
    return owner.equals(methodCall.owner)
        && name.equals(methodCall.name)
        && descriptor.equals(methodCall.desc);
  }

  private static boolean isVarInstruction(
      AbstractInsnNode instruction, int opcode, int variable) {
    return instruction instanceof VarInsnNode variableInstruction
        && variableInstruction.getOpcode() == opcode
        && variableInstruction.var == variable;
  }

  private static boolean isTimerFieldLoad(AbstractInsnNode instruction) {
    return instruction instanceof FieldInsnNode field
        && field.getOpcode() == Opcodes.GETSTATIC
        && TIMER_DESCRIPTOR.equals(field.desc);
  }

  private static boolean isTimerFieldLoad(AbstractInsnNode instruction, String fieldName) {
    return instruction instanceof FieldInsnNode field
        && field.getOpcode() == Opcodes.GETSTATIC
        && TIMER_DESCRIPTOR.equals(field.desc)
        && fieldName.equals(field.name);
  }

  private static boolean usesStartLocalAsSentinel(
      MethodNode method, int startLocal, AbstractInsnNode beforeTimerFieldLoad) {
    for (AbstractInsnNode instruction = method.instructions.getFirst();
        instruction != null && instruction != beforeTimerFieldLoad;
        instruction = instruction.getNext()) {
      if (isVarInstruction(instruction, Opcodes.LLOAD, startLocal)) {
        AbstractInsnNode next = nextMeaningful(instruction);
        if (next != null
            && (next.getOpcode() == Opcodes.LCMP
                || next.getOpcode() == Opcodes.LCONST_0
                || next.getOpcode() == Opcodes.LCONST_1)) {
          return true;
        }
      }
    }
    return false;
  }

  private static int indexOf(MethodNode method, AbstractInsnNode instruction) {
    InsnList instructions = method.instructions;
    return instructions.indexOf(instruction);
  }

  private record MethodKey(String name, String descriptor) {}

  private record InstrumentedSample(
      ClassNode classNode, Map<MethodKey, TimedMethodDescriptorEntry> timedMethods) {
    private MethodNode method(String methodName, String descriptor) {
      return findMethod(classNode, methodName, descriptor);
    }

    private String timerFieldName(String methodName, String descriptor) {
      TimedMethodDescriptorEntry timedMethod =
          timedMethods.get(new MethodKey(methodName, descriptor));
      assertNotNull(timedMethod);
      return timedMethod.timerFieldName();
    }
  }

  private record InstrumentationShape(
      int enabledLocal,
      int startLocal,
      MethodInsnNode entryEnabled,
      VarInsnNode entryEnabledLoad,
      JumpInsnNode entryEnabledJump,
      MethodInsnNode entryNano,
      AbstractInsnNode entryEnabledSkipTarget,
      VarInsnNode exitEntryEnabledLoad,
      JumpInsnNode exitEntryEnabledJump,
      AbstractInsnNode exitEntryEnabledSkipTarget,
      MethodInsnNode exitCurrentEnabled,
      JumpInsnNode exitCurrentEnabledJump,
      AbstractInsnNode exitCurrentEnabledSkipTarget,
      FieldInsnNode timerFieldLoad,
      JumpInsnNode timerBoundCheck,
      AbstractInsnNode unboundException,
      MethodInsnNode exitNano,
      VarInsnNode exitStartLoad,
      AbstractInsnNode subtract,
      MethodInsnNode timerRecord) {}
}
