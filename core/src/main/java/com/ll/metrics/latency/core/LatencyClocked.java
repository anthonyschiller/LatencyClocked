package com.ll.metrics.latency.core;

import com.ll.metrics.latency.constants.LatencyClockedConstants;
import com.ll.metrics.latency.hdr.HdrTimers;
import com.ll.metrics.latency.snapshot.TimerSnapshot;
import com.ll.metrics.latency.timer.Timers;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import javax.management.InstanceAlreadyExistsException;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point for startup class-index loading and generated timer binding. */
public final class LatencyClocked {
  private static final Logger LOGGER = LoggerFactory.getLogger(LatencyClocked.class);
  private static final Object INITIALISATION_LOCK = new Object();
  private static Timers owner;
  private static boolean initialising;
  private static boolean initialised;
  private static boolean managementRegistered;
  private static volatile boolean enabled = isEnabledPropertySet();

  private LatencyClocked() {}

  /**
   * Initializes generated timer fields using the single-writer HDR-backed timer catalogue.
   *
   * <p>Use {@link #initialisedThreadSafe()} when timers may be recorded by multiple threads.
   * Use {@link #initialise(Timers)} for tests or custom timer catalogues.
   *
   * @return runtime handle backed by single-writer HDR timers
   */
  public static LatencyClocked initialise() {
    return initialise(HdrTimers.create());
  }

  /**
   * Initializes generated timer fields from every instrumented-class index resource visible to the
   * context class loader and returns a runtime handle backed by the supplied timers.
   *
   * <p>This method is thread-safe and idempotent for the exact same {@link Timers} instance, said
   * owner. Once an owner has been selected, later calls with another {@link Timers} instance fail
   * because generated static timer fields cannot be safely rebound to another owner. If startup
   * fails partway through, it may be retried with the original owner. Synchronous recursive
   * initialization is unsupported.
   *
   * <p>This overload is intended for tests and custom timer implementations.
   *
   * @param timers timer owner used by generated bind methods
   * @return runtime handle backed by the supplied timers
   */
  public static LatencyClocked initialise(Timers timers) {
    Objects.requireNonNull(timers, "timers");
    LatencyClocked latencyClocked = new LatencyClocked();

    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    if (classLoader == null) {
      classLoader = LatencyClocked.class.getClassLoader();
    }

    synchronized (INITIALISATION_LOCK) {
      if (initialised) {
        requireSameOwner(timers);
        return latencyClocked;
      }

      if (initialising) {
        throw new IllegalStateException("Recursive LatencyClocked initialisation is unsupported");
      }

      if (owner == null) {
        owner = timers;
        enabled = isEnabledPropertySet();
        if (!enabled()) {
          LOGGER.info(
              "LatencyClocked recording disabled by system property {}=false",
              LatencyClockedConstants.ENABLED_PROPERTY);
        }
      } else {
        requireSameOwner(timers);
      }

      initialising = true;
      try {
        bindInstrumentedClasses(classLoader);
        registerManagementBean();
        initialised = true;
        return latencyClocked;
      } finally {
        initialising = false;
      }
    }
  }

  private static void bindInstrumentedClasses(ClassLoader classLoader) {
    try {
      Set<String> classNames = new LinkedHashSet<>();
      int resourceCount = 0;
      Enumeration<URL> resources =
          classLoader.getResources(LatencyClockedConstants.INSTRUMENTED_CLASS_INDEX_RESOURCE);
      while (resources.hasMoreElements()) {
        URL resource = resources.nextElement();
        resourceCount++;
        LOGGER.debug("Loading latency instrumented-class index resource {}", resource);
        loadInstrumentedClassIndex(resource, classNames);
      }
      for (String className : classNames) {
        bindClass(className, classLoader);
      }
      LOGGER.debug(
          "Initialized latency timers for {} classes from {} instrumented-class index resources",
          classNames.size(),
          resourceCount);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to load instrumented class-index resources named "
              + LatencyClockedConstants.INSTRUMENTED_CLASS_INDEX_RESOURCE,
          e);
    }
  }

  private static void requireSameOwner(Timers timers) {
    if (owner != timers) {
      throw new IllegalStateException(
          "LatencyClocked has already associated instrumented method timers with a different "
              + "Timers instance. Rebinding generated timer fields to another owner is "
              + "unsupported.");
    }
  }

  private static void resetForTests() {
    synchronized (INITIALISATION_LOCK) {
      unregisterManagementBean();
      owner = null;
      initialising = false;
      initialised = false;
      managementRegistered = false;
      enabled = isEnabledPropertySet();
    }
  }

  /**
   * Initializes generated timer fields using the thread-safe HDR-backed timer catalogue.
   *
   * <p>Production applications with concurrent request handling should prefer this method.
   * Use {@link #initialise()} for single-writer timers.
   *
   * @return runtime handle backed by thread-safe HDR timers
   */
  public static LatencyClocked initialisedThreadSafe() {
    return initialise(HdrTimers.createWithThreadsafeTimers());
  }

  /**
   * Returns immutable reporting snapshots for generated method timers.
   *
   * @return point-in-time timer snapshots
   */
  public static Collection<TimerSnapshot> snapshots() {
    if (owner == null) {
      return Collections.emptyList();
    }
    return owner.snapshots();
  }

  /**
   * Returns whether generated latency recording is currently enabled.
   *
   * @return true when generated timing code should run
   */
  public static boolean enabled() {
    return enabled;
  }

  /**
   * Enables or disables generated latency recording.
   *
   * <p>This mutates the same volatile state read by generated instrumentation and by the
   * LatencyClocked JMX management bean.
   *
   * @param enabled true to enable recording, false to discard timed invocations
   */
  public static void setEnabled(boolean enabled) {
    LatencyClocked.enabled = enabled;
  }

  private static void registerManagementBean() {
    if (managementRegistered) {
      return;
    }
    try {
      ObjectName objectName = new ObjectName(LatencyClockedConstants.MBEAN_NAME);
      MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
      if (mbeanServer.isRegistered(objectName)) {
        throw new IllegalStateException(
            "JMX object name "
                + LatencyClockedConstants.MBEAN_NAME
                + " is already registered by another component");
      }
      mbeanServer.registerMBean(
          new StandardMBean(
              new LatencyClockedManagement(), LatencyClockedManagementBean.class, true),
          objectName);
      managementRegistered = true;
      LOGGER.debug("Registered LatencyClocked management bean as {}", objectName);
    } catch (InstanceAlreadyExistsException e) {
      throw new IllegalStateException(
          "JMX object name "
              + LatencyClockedConstants.MBEAN_NAME
              + " was registered before LatencyClocked could register it",
          e);
    } catch (JMException e) {
      throw new IllegalStateException(
          "Failed to register LatencyClocked management bean "
              + LatencyClockedConstants.MBEAN_NAME,
          e);
    }
  }

  private static void unregisterManagementBean() {
    if (!managementRegistered) {
      return;
    }
    try {
      ObjectName objectName = new ObjectName(LatencyClockedConstants.MBEAN_NAME);
      MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
      if (mbeanServer.isRegistered(objectName)) {
        mbeanServer.unregisterMBean(objectName);
      }
      managementRegistered = false;
    } catch (JMException e) {
      throw new IllegalStateException(
          "Failed to unregister LatencyClocked management bean "
              + LatencyClockedConstants.MBEAN_NAME,
          e);
    }
  }

  private static boolean isEnabledPropertySet() {
    return !"false".equalsIgnoreCase(System.getProperty(LatencyClockedConstants.ENABLED_PROPERTY));
  }

  private static void loadInstrumentedClassIndex(URL resource, Set<String> classNames)
      throws IOException {
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
              "Malformed instrumented class-index line at "
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

  private static void bindClass(String className, ClassLoader classLoader) {
    Class<?> targetClass;
    try {
      targetClass = Class.forName(className, true, classLoader);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(
          "Instrumented-class index references missing class '"
              + className
              + "' in "
              + LatencyClockedConstants.INSTRUMENTED_CLASS_INDEX_RESOURCE,
          e);
    }

    Method bindMethod;
    try {
      bindMethod = targetClass.getDeclaredMethod(LatencyClockedConstants.BIND_METHOD, Timers.class);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(
          "Instrumented-class index class '"
              + className
              + "' from "
              + LatencyClockedConstants.INSTRUMENTED_CLASS_INDEX_RESOURCE
              + " is missing generated bind method "
              + LatencyClockedConstants.BIND_METHOD
              + "("
              + Timers.class.getName()
              + ")",
          e);
    }

    if (!Modifier.isStatic(bindMethod.getModifiers())) {
      throw new IllegalStateException(
          "Instrumented-class index bind method '"
              + className
              + LatencyClockedConstants.CLASS_NAME_SEPARATOR
              + LatencyClockedConstants.BIND_METHOD
              + "' must be static");
    }

    try {
      if (!bindMethod.canAccess(null)) {
        bindMethod.setAccessible(true);
      }
      bindMethod.invoke(null, owner);
      LOGGER.debug("Invoked bind method for {}", className);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(
          "Illegal access invoking bind method '"
              + className
              + LatencyClockedConstants.CLASS_NAME_SEPARATOR
              + LatencyClockedConstants.BIND_METHOD
              + "'",
          e);
    } catch (InvocationTargetException e) {
      throw new IllegalStateException(
          "Invocation failure in bind method '"
              + className
              + LatencyClockedConstants.CLASS_NAME_SEPARATOR
              + LatencyClockedConstants.BIND_METHOD
              + "': "
              + e.getCause().getMessage(),
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
