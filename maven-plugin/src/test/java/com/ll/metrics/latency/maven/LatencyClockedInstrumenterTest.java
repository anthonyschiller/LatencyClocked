package com.ll.metrics.latency.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ll.metrics.latency.constants.LatencyClockedConstants;
import com.ll.metrics.latency.maven.model.TimedMethodDescriptorEntry;
import com.ll.metrics.latency.maven.samples.SampleTimedClass;
import com.ll.metrics.latency.timer.InMemoryTimers;
import com.ll.metrics.latency.timer.Timer;
import com.ll.metrics.latency.timer.Timers;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

class LatencyClockedInstrumenterTest {
  private static final String SAMPLE_CLASS_NAME = SampleTimedClass.class.getName();

  @Test
  void scanRejectsNullOutputDirectory() {
    assertThrows(NullPointerException.class, () -> LatencyClockedInstrumenter.scan(null));
  }

  @Test
  void scanReturnsTimedMethodsByClassFile() throws IOException {
    Path outputDirectory = Path.of("target", "test-classes");
    Map<Path, List<TimedMethodDescriptorEntry>> timedMethodsByClassFile =
        LatencyClockedInstrumenter.scan(outputDirectory);
    Path sampleClassFile = classFile(outputDirectory, SampleTimedClass.class);

    assertTrue(timedMethodsByClassFile.containsKey(sampleClassFile));
    assertEquals(5, timedMethodsByClassFile.get(sampleClassFile).size());
    assertTrue(
        timedMethodsByClassFile.get(sampleClassFile).stream()
            .allMatch(timedMethod -> timedMethod.className().equals(SAMPLE_CLASS_NAME)));
  }

  @Test
  void scanIncludesOnlyTimedMethods() throws IOException {
    List<TimedMethodDescriptorEntry> timedMethods =
        timedMethodsIn(LatencyClockedInstrumenter.scan(Path.of("target", "test-classes")));

    List<TimedMethodDescriptorEntry> sampleTimedMethods =
        timedMethods.stream()
            .filter(timedMethod -> timedMethod.className().equals(SAMPLE_CLASS_NAME))
            .toList();

    assertEquals(5, sampleTimedMethods.size());
    assertTrue(
        sampleTimedMethods.stream()
            .noneMatch(
                timedMethod ->
                    timedMethod.timerId().startsWith(SAMPLE_CLASS_NAME + "#normalMethod")));
  }

  @Test
  void scanUsesGeneratedMethodIdForTimedMethods() throws IOException {
    List<TimedMethodDescriptorEntry> timedMethods =
        timedMethodsIn(LatencyClockedInstrumenter.scan(Path.of("target", "test-classes")));

    assertTrue(
        timedMethods.stream()
            .anyMatch(
                timedMethod ->
                    timedMethod.className().equals(SAMPLE_CLASS_NAME)
                        && timedMethod
                            .timerId()
                            .equals(SAMPLE_CLASS_NAME + "#timedMethodWithGeneratedId()V")));
  }

  @Test
  void scanAddsParameterTypesForOverloadedTimedMethods() throws IOException {
    List<TimedMethodDescriptorEntry> timedMethods =
        timedMethodsIn(LatencyClockedInstrumenter.scan(Path.of("target", "test-classes")));

    Set<String> timerIds =
        timedMethods.stream()
            .filter(timedMethod -> timedMethod.className().equals(SAMPLE_CLASS_NAME))
            .map(TimedMethodDescriptorEntry::timerId)
            .collect(Collectors.toSet());

    assertTrue(timerIds.contains(SAMPLE_CLASS_NAME + "#overloaded(I)V"));
    assertTrue(timerIds.contains(SAMPLE_CLASS_NAME + "#overloaded(Ljava/lang/String;)V"));
  }

  @Test
  void scanUsesSimpleDefaultTimerIdForNonOverloadedTimedMethod() throws IOException {
    List<TimedMethodDescriptorEntry> timedMethods =
        timedMethodsIn(LatencyClockedInstrumenter.scan(Path.of("target", "test-classes")));

    assertTrue(
        timedMethods.stream()
            .anyMatch(
                timedMethod ->
                    timedMethod.className().equals(SAMPLE_CLASS_NAME)
                        && timedMethod.timerId().equals(SAMPLE_CLASS_NAME + "#timedMethod()V")));
  }

  @Test
  void scanIncludesTimedStaticMethods() throws IOException {
    List<TimedMethodDescriptorEntry> timedMethods =
        timedMethodsIn(LatencyClockedInstrumenter.scan(Path.of("target", "test-classes")));

    assertTrue(
        timedMethods.stream()
            .anyMatch(
                timedMethod ->
                    timedMethod.className().equals(SAMPLE_CLASS_NAME)
                        && timedMethod
                            .timerId()
                            .equals(SAMPLE_CLASS_NAME + "#staticTimedMethod()V")));
  }

  @Test
  void generatedTimerFieldNamesAreSequenced() throws IOException {
    List<TimedMethodDescriptorEntry> timedMethods =
        timedMethodsIn(LatencyClockedInstrumenter.scan(Path.of("target", "test-classes")));

    List<TimedMethodDescriptorEntry> sampleTimedMethods =
        timedMethods.stream()
            .filter(timedMethod -> timedMethod.className().equals(SAMPLE_CLASS_NAME))
            .toList();

    assertEquals(
        Set.of(
            "__latency_clocked_timer_0",
            "__latency_clocked_timer_1",
            "__latency_clocked_timer_2",
            "__latency_clocked_timer_3",
            "__latency_clocked_timer_4"),
        sampleTimedMethods.stream()
            .map(TimedMethodDescriptorEntry::fieldName)
            .collect(Collectors.toSet()));
  }

  @Test
  void writeIndexWritesTimerIndex(@TempDir Path outputDirectory) throws IOException {
    List<TimedMethodDescriptorEntry> timedMethods =
        List.of(
            new TimedMethodDescriptorEntry(
                "com.example.Service", "call", "()V", "__latency_clocked_timer_0", "id"));

    LatencyClockedInstrumenter.generateInstrumentedClassIndexFile(outputDirectory, timedMethods);

    Path descriptor = descriptor(outputDirectory);
    assertEquals("com.example.Service", Files.readString(descriptor).trim());
  }

  @Test
  void writeIndexDeduplicatesClassNames(@TempDir Path outputDirectory) throws IOException {
    LatencyClockedInstrumenter.generateInstrumentedClassIndexFile(
        outputDirectory,
        List.of(
            new TimedMethodDescriptorEntry(
                "com.example.Service", "call", "()V", "__latency_clocked_timer_0", "a"),
            new TimedMethodDescriptorEntry(
                "com.example.Service", "other", "()V", "__latency_clocked_timer_1", "b")));

    assertEquals("com.example.Service", Files.readString(descriptor(outputDirectory)).trim());
  }

  @Test
  void generatedIndexIsIncludedInPackagedJar(@TempDir Path outputDirectory) throws IOException {
    copyClassToOutputDirectory(SampleTimedClass.class, outputDirectory);
    scanInstrumentAndWriteIndex(outputDirectory);
    Path jar = outputDirectory.resolve("sample.jar");

    try (JarOutputStream outputStream = new JarOutputStream(Files.newOutputStream(jar))) {
      String indexEntry = LatencyClockedConstants.DESCRIPTOR_RESOURCE;
      outputStream.putNextEntry(new JarEntry(indexEntry));
      outputStream.write(Files.readAllBytes(descriptor(outputDirectory)));
      outputStream.closeEntry();
    }

    try (JarFile jarFile = new JarFile(jar.toFile())) {
      JarEntry index = jarFile.getJarEntry(LatencyClockedConstants.DESCRIPTOR_RESOURCE);
      assertNotNull(index);
      String indexContent =
          new String(jarFile.getInputStream(index).readAllBytes(), StandardCharsets.UTF_8);
      assertEquals(SAMPLE_CLASS_NAME, indexContent.trim());
      assertTrue(indexContent.lines().noneMatch(line -> line.contains("|")));
    }
  }

  @Test
  void writeIndexSkipsEmptyIndex(@TempDir Path outputDirectory) throws IOException {
    LatencyClockedInstrumenter.generateInstrumentedClassIndexFile(outputDirectory, List.of());

    Path descriptor = descriptor(outputDirectory);
    assertTrue(Files.notExists(descriptor));
  }

  @Test
  void writeIndexRemovesStaleIndexWhenNoTimedMethodsRemain(@TempDir Path outputDirectory)
      throws IOException {
    Path descriptor = descriptor(outputDirectory);
    Files.createDirectories(descriptor.getParent());
    Files.writeString(descriptor, "stale");

    LatencyClockedInstrumenter.generateInstrumentedClassIndexFile(outputDirectory, List.of());

    assertTrue(Files.notExists(descriptor));
  }

  @Test
  void injectsTimerFieldForOneTimedMethod(@TempDir Path outputDirectory) throws IOException {
    copyClassToOutputDirectory(SampleTimedClass.class, outputDirectory);

    List<TimedMethodDescriptorEntry> timedMethods = scanInstrumentAndWriteIndex(outputDirectory);

    assertEquals(5, timedMethods.size());
    TimedMethodDescriptorEntry timedMethod = timedMethods.getFirst();
    FieldInfo field =
        requiredField(outputDirectory, SampleTimedClass.class, timedMethod.fieldName());
    assertEquals("__latency_clocked_timer_0", field.name());
    assertTrue(field.isStatic());
    assertTrue(field.isSynthetic());
    assertTrue(field.isPrivate());
    assertEquals(Type.getDescriptor(Timer.class), field.descriptor());
    assertEquals(SAMPLE_CLASS_NAME, Files.readString(descriptor(outputDirectory)).trim());
  }

  @Test
  void injectsTimerFieldsForMultipleTimedMethods(@TempDir Path outputDirectory) throws IOException {
    copyClassToOutputDirectory(SampleTimedClass.class, outputDirectory);

    List<TimedMethodDescriptorEntry> timedMethods = scanInstrumentAndWriteIndex(outputDirectory);

    assertEquals(5, timedMethods.size());
    Set<String> fieldNames =
        fields(outputDirectory, SampleTimedClass.class).stream()
            .map(FieldInfo::name)
            .collect(Collectors.toSet());
    assertTrue(fieldNames.contains("__latency_clocked_timer_0"));
    assertTrue(fieldNames.contains("__latency_clocked_timer_1"));
    assertTrue(fieldNames.contains("__latency_clocked_timer_2"));
    assertTrue(fieldNames.contains("__latency_clocked_timer_3"));
    assertTrue(fieldNames.contains("__latency_clocked_timer_4"));

    assertEquals(SAMPLE_CLASS_NAME, Files.readString(descriptor(outputDirectory)).trim());
  }

  @Test
  void injectsTimerFieldForStaticTimedMethod(@TempDir Path outputDirectory) throws IOException {
    copyClassToOutputDirectory(SampleTimedClass.class, outputDirectory);

    List<TimedMethodDescriptorEntry> timedMethods = scanInstrumentAndWriteIndex(outputDirectory);

    TimedMethodDescriptorEntry staticTimedMethod =
        timedMethods.stream()
            .filter(
                timedMethod ->
                    timedMethod.timerId().equals(SAMPLE_CLASS_NAME + "#staticTimedMethod()V"))
            .findFirst()
            .orElseThrow();
    FieldInfo field =
        requiredField(outputDirectory, SampleTimedClass.class, staticTimedMethod.fieldName());
    assertTrue(field.isStatic());
    assertEquals(Type.getDescriptor(Timer.class), field.descriptor());
  }

  @Test
  void injectsGeneratedBindMethod(@TempDir Path outputDirectory) throws IOException {
    copyClassToOutputDirectory(SampleTimedClass.class, outputDirectory);

    scanInstrumentAndWriteIndex(outputDirectory);

    MethodInfo method =
        requiredMethod(outputDirectory, SampleTimedClass.class, "__latency_clocked$bind");
    assertTrue(method.isStatic());
    assertTrue(method.isSynthetic());
    assertEquals("(" + Type.getDescriptor(Timers.class) + ")V", method.descriptor());
  }

  @Test
  void generatedBindMethodAssignsTimerFields(@TempDir Path outputDirectory) throws Exception {
    copyClassToOutputDirectory(SampleTimedClass.class, outputDirectory);

    List<TimedMethodDescriptorEntry> timedMethods = scanInstrumentAndWriteIndex(outputDirectory);
    TimedMethodDescriptorEntry timedMethod = timedMethods.getFirst();
    Class<?> instrumentedClass = loadInstrumentedClass(outputDirectory, SampleTimedClass.class);
    Timers timers = InMemoryTimers.create();

    Method bindMethod = instrumentedClass.getDeclaredMethod("__latency_clocked$bind", Timers.class);
    bindMethod.setAccessible(true);
    bindMethod.invoke(null, timers);

    Field field = instrumentedClass.getDeclaredField(timedMethod.fieldName());
    field.setAccessible(true);
    assertEquals(timers.claim(timedMethod.timerId()), field.get(null));
  }

  @Test
  void injectingTimerFieldsAndBindMethodIsIdempotent(@TempDir Path outputDirectory)
      throws IOException {
    copyClassToOutputDirectory(SampleTimedClass.class, outputDirectory);
    Map<Path, List<TimedMethodDescriptorEntry>> timedMethodsByClassFile =
        LatencyClockedInstrumenter.scan(outputDirectory);

    LatencyClockedInstrumenter.InjectionResult firstInjection =
        LatencyClockedInstrumenter.instrument(timedMethodsByClassFile);
    LatencyClockedInstrumenter.InjectionResult secondInjection =
        LatencyClockedInstrumenter.instrument(timedMethodsByClassFile);

    assertEquals(5, firstInjection.injectedFields());
    assertEquals(0, firstInjection.skippedFields());
    assertEquals(0, secondInjection.injectedFields());
    assertEquals(5, secondInjection.skippedFields());
    assertEquals(1, firstInjection.injectedBindMethods());
    assertEquals(0, firstInjection.skippedBindMethods());
    assertEquals(0, secondInjection.injectedBindMethods());
    assertEquals(1, secondInjection.skippedBindMethods());
    assertEquals(
        1,
        fields(outputDirectory, SampleTimedClass.class).stream()
            .filter(field -> field.name().equals("__latency_clocked_timer_0"))
            .count());
    assertEquals(
        1,
        methods(outputDirectory, SampleTimedClass.class).stream()
            .filter(method -> method.name().equals("__latency_clocked$bind"))
            .count());
  }

  private static List<TimedMethodDescriptorEntry> scanInstrumentAndWriteIndex(Path outputDirectory)
      throws IOException {
    Map<Path, List<TimedMethodDescriptorEntry>> timedMethodsByClassFile =
        LatencyClockedInstrumenter.scan(outputDirectory);
    LatencyClockedInstrumenter.instrument(timedMethodsByClassFile);
    List<TimedMethodDescriptorEntry> timedMethods =
        timedMethodsByClassFile.values().stream().flatMap(List::stream).toList();
    LatencyClockedInstrumenter.generateInstrumentedClassIndexFile(outputDirectory, timedMethods);
    return timedMethodsIn(timedMethodsByClassFile);
  }

  private static List<TimedMethodDescriptorEntry> timedMethodsIn(
      Map<Path, List<TimedMethodDescriptorEntry>> timedMethodsByClassFile) {
    return timedMethodsByClassFile.values().stream().flatMap(List::stream).toList();
  }

  private static void copyClassToOutputDirectory(Class<?> sourceClass, Path outputDirectory)
      throws IOException {
    Path classFile = classFile(outputDirectory, sourceClass);
    Files.createDirectories(classFile.getParent());
    Files.copy(classFile(Path.of("target", "test-classes"), sourceClass), classFile);
  }

  private static Path classFile(Path outputDirectory, Class<?> sourceClass) {
    return outputDirectory.resolve(
        sourceClass
                .getName()
                .replace(
                    LatencyClockedConstants.CLASS_NAME_SEPARATOR,
                    LatencyClockedConstants.RESOURCE_PATH_SEPARATOR)
            + LatencyClockedConstants.CLASS_FILE_EXTENSION);
  }

  private static Path descriptor(Path outputDirectory) {
    return outputDirectory
        .resolve(LatencyClockedConstants.DESCRIPTOR_ROOT)
        .resolve(LatencyClockedConstants.DESCRIPTOR_DIRECTORY)
        .resolve(LatencyClockedConstants.DESCRIPTOR_FILE);
  }

  private static FieldInfo requiredField(
      Path outputDirectory, Class<?> sourceClass, String fieldName) throws IOException {
    return fields(outputDirectory, sourceClass).stream()
        .filter(field -> field.name().equals(fieldName))
        .findFirst()
        .orElseThrow();
  }

  private static List<FieldInfo> fields(Path outputDirectory, Class<?> sourceClass)
      throws IOException {
    List<FieldInfo> fields = new ArrayList<>();
    try (InputStream inputStream = Files.newInputStream(classFile(outputDirectory, sourceClass))) {
      ClassReader reader = new ClassReader(inputStream);
      reader.accept(
          new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                int access, String name, String descriptor, String signature, Object value) {
              fields.add(new FieldInfo(name, access, descriptor));
              return null;
            }
          },
          ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
    return fields;
  }

  private static MethodInfo requiredMethod(
      Path outputDirectory, Class<?> sourceClass, String methodName) throws IOException {
    return methods(outputDirectory, sourceClass).stream()
        .filter(method -> method.name().equals(methodName))
        .findFirst()
        .orElseThrow();
  }

  private static List<MethodInfo> methods(Path outputDirectory, Class<?> sourceClass)
      throws IOException {
    List<MethodInfo> methods = new ArrayList<>();
    try (InputStream inputStream = Files.newInputStream(classFile(outputDirectory, sourceClass))) {
      ClassReader reader = new ClassReader(inputStream);
      reader.accept(
          new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
              methods.add(new MethodInfo(name, access, descriptor));
              return null;
            }
          },
          ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
    return methods;
  }

  private static Class<?> loadInstrumentedClass(Path outputDirectory, Class<?> sourceClass)
      throws IOException {
    byte[] classBytes = Files.readAllBytes(classFile(outputDirectory, sourceClass));
    return new SingleClassLoader(sourceClass.getName(), classBytes).load();
  }

  private record FieldInfo(String name, int access, String descriptor) {
    private boolean isPrivate() {
      return (access & Opcodes.ACC_PRIVATE) != 0;
    }

    private boolean isStatic() {
      return (access & Opcodes.ACC_STATIC) != 0;
    }

    private boolean isSynthetic() {
      return (access & Opcodes.ACC_SYNTHETIC) != 0;
    }
  }

  private record MethodInfo(String name, int access, String descriptor) {
    private boolean isStatic() {
      return (access & Opcodes.ACC_STATIC) != 0;
    }

    private boolean isSynthetic() {
      return (access & Opcodes.ACC_SYNTHETIC) != 0;
    }
  }

  private static final class SingleClassLoader extends ClassLoader {
    private final String className;
    private final byte[] classBytes;

    private SingleClassLoader(String className, byte[] classBytes) {
      super(LatencyClockedInstrumenterTest.class.getClassLoader());
      this.className = className;
      this.classBytes = classBytes.clone();
    }

    private Class<?> load() {
      return defineClass(className, classBytes, 0, classBytes.length);
    }
  }
}
