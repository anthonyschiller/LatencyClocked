package com.ll.metrics.latency.core;

import com.ll.metrics.latency.constants.LatencyClockedConstants;
import com.ll.metrics.latency.hdr.HdrTimers;
import com.ll.metrics.latency.timer.Timer;
import com.ll.metrics.latency.timer.Timers;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point for startup descriptor loading and timer lookup. */
public final class LatencyClocked {
  private static final Logger LOGGER = LoggerFactory.getLogger(LatencyClocked.class);
  private static volatile boolean enabled = isEnabledPropertySet();

  private final Timers timers;

  private LatencyClocked(Timers timers) {
    this.timers = Objects.requireNonNull(timers, "timers");
  }

  /**
   * Initializes generated timer fields using the single-writer HDR-backed timer catalogue.
   *
   * <p>Use {@link #initialisedThreadSafe()} when timers may be recorded by multiple threads.
   * Use {@link #initialise(Timers)} for tests or custom timer catalogues.
   */
  public static LatencyClocked initialise() {
    return initialise(HdrTimers.create());
  }

  /**
   * Initializes generated timer fields from every latency descriptor resource visible to the
   * context class loader and returns a runtime handle backed by the supplied timers.
   *
   * <p>This overload is intended for tests and custom timer implementations.
   */
  public static LatencyClocked initialise(Timers timers) {
    LatencyClocked latencyClocked = new LatencyClocked(timers);
    enabled = isEnabledPropertySet();
    if (!enabled()) {
      LOGGER.info(
          "LatencyClocked disabled by system property {}=false",
          LatencyClockedConstants.ENABLED_PROPERTY);
      return latencyClocked;
    }

    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    if (classLoader == null) {
      classLoader = LatencyClocked.class.getClassLoader();
    }

    try {
      Set<String> classNames = new LinkedHashSet<>();
      int resourceCount = 0;
      Enumeration<URL> resources =
          classLoader.getResources(LatencyClockedConstants.DESCRIPTOR_RESOURCE);
      while (resources.hasMoreElements()) {
        URL resource = resources.nextElement();
        resourceCount++;
        LOGGER.debug("Loading latency descriptor resource {}", resource);
        loadDescriptor(resource, classNames);
      }
      for (String className : classNames) {
        bindClass(className, classLoader, timers);
      }
      LOGGER.debug(
          "Initialized latency timers for {} classes from {} descriptor resources",
          classNames.size(),
          resourceCount);
      return latencyClocked;
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to load latency descriptor resources named "
              + LatencyClockedConstants.DESCRIPTOR_RESOURCE,
          e);
    }
  }

  /**
   * Initializes generated timer fields using the thread-safe HDR-backed timer catalogue.
   *
   * <p>Production applications with concurrent request handling should prefer this method.
   * Use {@link #initialise()} for single-writer timers.
   */
  public static LatencyClocked initialisedThreadSafe() {
    return initialise(HdrTimers.createWithThreadsafeTimers());
  }

  /** Returns or creates a timer from the initialized timer catalogue. */
  public Timer timer(String id) {
    return timers.timer(id);
  }

  /** Returns whether generated latency recording is currently enabled. */
  public static boolean enabled() {
    return enabled;
  }

  private static boolean isEnabledPropertySet() {
    return !"false".equalsIgnoreCase(System.getProperty(LatencyClockedConstants.ENABLED_PROPERTY));
  }

  private static void loadDescriptor(URL resource, Set<String> classNames) throws IOException {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8))) {
      String line;
      int lineNumber = 0;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
          continue;
        }
        if (!isClassName(trimmed)) {
          throw new IllegalStateException(
              "Malformed latency descriptor line at "
                  + resource
                  + ":"
                  + lineNumber
                  + ". Expected <fully.qualified.ClassName> but found: "
                  + line);
        }
        classNames.add(trimmed);
      }
    }
  }

  private static void bindClass(String className, ClassLoader classLoader, Timers timers) {
    Class<?> targetClass;
    try {
      targetClass = Class.forName(className, true, classLoader);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(
          "Latency descriptor references missing class '"
              + className
              + "' in latency descriptor "
              + LatencyClockedConstants.DESCRIPTOR_RESOURCE,
          e);
    }

    Method bindMethod;
    try {
      bindMethod = targetClass.getDeclaredMethod(LatencyClockedConstants.BIND_METHOD, Timers.class);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(
          "Latency descriptor class '"
              + className
              + "' is missing generated bind method "
              + LatencyClockedConstants.BIND_METHOD
              + "("
              + Timers.class.getName()
              + ")",
          e);
    }

    if (!Modifier.isStatic(bindMethod.getModifiers())) {
      throw new IllegalStateException(
          "Latency descriptor bind method '"
              + className
              + LatencyClockedConstants.CLASS_NAME_SEPARATOR
              + LatencyClockedConstants.BIND_METHOD
              + "' must be static");
    }

    try {
      if (!bindMethod.canAccess(null)) {
        bindMethod.setAccessible(true);
      }
      bindMethod.invoke(null, timers);
      LOGGER.debug("Invoked latency bind method for {}", className);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(
          "Illegal access invoking latency bind method '"
              + className
              + LatencyClockedConstants.CLASS_NAME_SEPARATOR
              + LatencyClockedConstants.BIND_METHOD
              + "'",
          e);
    } catch (InvocationTargetException e) {
      throw new IllegalStateException(
          "Invocation failure in latency bind method '"
              + className
              + LatencyClockedConstants.CLASS_NAME_SEPARATOR
              + LatencyClockedConstants.BIND_METHOD
              + "'",
          e.getCause());
    }
  }

  private static boolean isClassName(String value) {
    String[] parts = value.split(Pattern.quote(LatencyClockedConstants.CLASS_NAME_SEPARATOR), -1);
    if (parts.length == 0) {
      return false;
    }
    for (String part : parts) {
      if (!isIdentifier(part)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isIdentifier(String value) {
    if (value.isEmpty() || !Character.isJavaIdentifierStart(value.charAt(0))) {
      return false;
    }
    for (int i = 1; i < value.length(); i++) {
      if (!Character.isJavaIdentifierPart(value.charAt(i))) {
        return false;
      }
    }
    return true;
  }
}
