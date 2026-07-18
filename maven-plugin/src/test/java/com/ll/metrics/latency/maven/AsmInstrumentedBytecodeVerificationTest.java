package com.ll.metrics.latency.maven;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ll.metrics.latency.constants.LatencyClockedConstants;
import com.ll.metrics.latency.maven.model.TimedMethodDescriptorEntry;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class AsmInstrumentedBytecodeVerificationTest {
  private static final String SAMPLE_CLASS_NAME = "golden.GoldenTimedSamples";
  private static final String SAMPLE_CLASS_RESOURCE =
      "golden/GoldenTimedSamples" + LatencyClockedConstants.CLASS_FILE_EXTENSION;

  @Test
  void instrumentedGoldenClassLoadsWithoutLinkageErrors(@TempDir Path outputDirectory)
      throws Exception {
    compileAndInstrument(outputDirectory);

    try (URLClassLoader classLoader =
        new URLClassLoader(
            new URL[] {outputDirectory.toUri().toURL()},
            AsmInstrumentedBytecodeVerificationTest.class.getClassLoader())) {
      assertDoesNotThrow(() -> Class.forName(SAMPLE_CLASS_NAME, true, classLoader));
    }
  }

  @Test
  void timedMethodsContainNanoTimeAndTimerRecordCalls(@TempDir Path outputDirectory)
      throws Exception {
    compileAndInstrument(outputDirectory);

    MethodCalls calls = methodCalls(outputDirectory, "successfulVoid", "()V");

    assertEquals(2, calls.countCall("java/lang/System", "nanoTime", "()J"));
    assertEquals(
        2,
        calls.countCall(
            "com/ll/metrics/latency/core/LatencyClocked", "enabled", "()Z"));
    assertTrue(calls.callsTimerRecord());
    assertTrue(calls.firstEnabledCheckPrecedesEntryNanoTime());
    assertTrue(calls.invocationEnabledLocalIsTestedBeforeExitEnabledCheck());
    assertTrue(calls.exitEnabledCheckPrecedesTimerRecord());
  }

  @Test
  void nonAnnotatedMethodsDoNotCallTimerRecord(@TempDir Path outputDirectory) throws Exception {
    compileAndInstrument(outputDirectory);

    MethodCalls calls = methodCalls(outputDirectory, "normalMethod", "()V");

    assertFalse(calls.callsTimerRecord());
  }

  @Test
  void throwingOnlyTimedMethodsDoNotCallTimerRecord(@TempDir Path outputDirectory)
      throws Exception {
    compileAndInstrument(outputDirectory);

    MethodCalls calls = methodCalls(outputDirectory, "throwingMethod", "()V");

    assertTrue(calls.hasThrowInstruction());
    assertFalse(calls.callsTimerRecord());
  }

  @Test
  void pluginRunTwiceProducesSameClassIndex(@TempDir Path outputDirectory) throws Exception {
    GoldenFixtureCompiler.compile(outputDirectory, "GoldenTimedSamples.java");
    instrument(outputDirectory);
    String firstIndex = Files.readString(descriptor(outputDirectory));

    instrument(outputDirectory);
    String secondIndex = Files.readString(descriptor(outputDirectory));

    assertEquals(firstIndex, secondIndex);
    assertEquals(SAMPLE_CLASS_NAME, secondIndex.trim());
  }

  private static void compileAndInstrument(Path outputDirectory) throws IOException {
    GoldenFixtureCompiler.compile(outputDirectory, "GoldenTimedSamples.java");
    instrument(outputDirectory);
  }

  private static void instrument(Path outputDirectory) throws IOException {
    Map<Path, List<TimedMethodDescriptorEntry>> timedMethodsByClassFile =
        LatencyClockedInstrumenter.scan(outputDirectory);
    LatencyClockedInstrumenter.instrument(timedMethodsByClassFile);
    LatencyClockedInstrumenter.generateInstrumentedClassIndexResource(
        outputDirectory, timedMethodsByClassFile.values().stream().flatMap(List::stream).toList());
  }

  private static MethodCalls methodCalls(Path outputDirectory, String methodName, String descriptor)
      throws IOException {
    List<MethodCall> calls = new ArrayList<>();
    List<String> events = new ArrayList<>();
    boolean[] hasThrowInstruction = new boolean[1];
    Path classFile = outputDirectory.resolve(SAMPLE_CLASS_RESOURCE);
    try (InputStream inputStream = Files.newInputStream(classFile)) {
      new ClassReader(inputStream)
          .accept(
              new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String methodDescriptor,
                    String signature,
                    String[] exceptions) {
                  if (!methodName.equals(name) || !descriptor.equals(methodDescriptor)) {
                    return null;
                  }
                  return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                        int opcode,
                        String owner,
                        String name,
                        String descriptor,
                        boolean isInterface) {
                      calls.add(new MethodCall(owner, name, descriptor));
                      events.add("CALL " + owner + "." + name + descriptor);
                    }

                    @Override
                    public void visitVarInsn(int opcode, int variable) {
                      if (opcode == Opcodes.ILOAD) {
                        events.add("ILOAD");
                      }
                    }

                    @Override
                    public void visitJumpInsn(int opcode, org.objectweb.asm.Label label) {
                      if (opcode == Opcodes.IFEQ) {
                        events.add("IFEQ");
                      }
                    }

                    @Override
                    public void visitInsn(int opcode) {
                      if (opcode == Opcodes.ATHROW) {
                        hasThrowInstruction[0] = true;
                      }
                    }
                  };
                }
              },
              ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
    return new MethodCalls(List.copyOf(calls), List.copyOf(events), hasThrowInstruction[0]);
  }

  private static Path descriptor(Path outputDirectory) {
    return outputDirectory
        .resolve(LatencyClockedConstants.INSTRUMENTED_CLASS_INDEX_ROOT)
        .resolve(LatencyClockedConstants.INSTRUMENTED_CLASS_INDEX_DIRECTORY)
        .resolve(LatencyClockedConstants.INSTRUMENTED_CLASS_INDEX_FILE);
  }

  private record MethodCalls(
      List<MethodCall> calls, List<String> events, boolean hasThrowInstruction) {
    private long countCall(String owner, String name, String descriptor) {
      return calls.stream()
          .filter(
              call ->
                  owner.equals(call.owner())
                      && name.equals(call.name())
                      && descriptor.equals(call.descriptor()))
          .count();
    }

    private boolean callsSystemNanoTime() {
      return calls.stream()
          .anyMatch(
              call ->
                  "java/lang/System".equals(call.owner()) && "nanoTime".equals(call.name()));
    }

    private boolean callsTimerRecord() {
      return calls.stream()
          .anyMatch(
                  call ->
                  "com/ll/metrics/latency/timer/Timer".equals(call.owner())
                      && "record".equals(call.name())
                      && "(J)V".equals(call.descriptor()));
    }

    private boolean firstEnabledCheckPrecedesEntryNanoTime() {
      return indexOfCall("com/ll/metrics/latency/core/LatencyClocked", "enabled", "()Z", 0)
          < indexOfCall("java/lang/System", "nanoTime", "()J", 0);
    }

    private boolean invocationEnabledLocalIsTestedBeforeExitEnabledCheck() {
      int secondEnabled =
          indexOfEvent(
              "CALL com/ll/metrics/latency/core/LatencyClocked.enabled()Z", 1);
      int loadLocal = events.indexOf("ILOAD");
      int branch = events.indexOf("IFEQ");
      return loadLocal >= 0 && branch > loadLocal && branch < secondEnabled;
    }

    private boolean exitEnabledCheckPrecedesTimerRecord() {
      return indexOfCall("com/ll/metrics/latency/core/LatencyClocked", "enabled", "()Z", 1)
          < indexOfCall("com/ll/metrics/latency/timer/Timer", "record", "(J)V", 0);
    }

    private int indexOfCall(String owner, String name, String descriptor, int occurrence) {
      int found = 0;
      for (int index = 0; index < calls.size(); index++) {
        MethodCall call = calls.get(index);
        if (owner.equals(call.owner())
            && name.equals(call.name())
            && descriptor.equals(call.descriptor())) {
          if (found == occurrence) {
            return index;
          }
          found++;
        }
      }
      return -1;
    }

    private int indexOfEvent(String event, int occurrence) {
      int found = 0;
      for (int index = 0; index < events.size(); index++) {
        if (event.equals(events.get(index))) {
          if (found == occurrence) {
            return index;
          }
          found++;
        }
      }
      return -1;
    }
  }

  private record MethodCall(String owner, String name, String descriptor) {}
}
