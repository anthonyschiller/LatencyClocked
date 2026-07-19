package com.ll.metrics.latency.maven.asm;

import com.ll.metrics.latency.constants.LatencyClockedConstants;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

/** ASM names and descriptors shared by the latency-clocked bytecode enhancer. */
public final class AsmConstants {
  public static final String TIMER_DESCRIPTOR = "Lcom/ll/metrics/latency/timer/Timer;";
  public static final String LATENCY_CLOCKED_INTERNAL_NAME =
      "com/ll/metrics/latency/core/LatencyClocked";
  public static final String TIMER_INTERNAL_NAME = "com/ll/metrics/latency/timer/Timer";
  public static final String TIMERS_INTERNAL_NAME = "com/ll/metrics/latency/timer/Timers";
  public static final String TIMERS_DESCRIPTOR = "L" + TIMERS_INTERNAL_NAME + ";";
  public static final String BIND_METHOD_NAME = LatencyClockedConstants.BIND_METHOD;
  public static final String BIND_METHOD_DESCRIPTOR = "(" + TIMERS_DESCRIPTOR + ")V";
  public static final String TIMER_RECORD_METHOD_NAME =
      LatencyClockedConstants.TIMER_RECORD_METHOD_NAME;
  public static final Method NANO_TIME = Method.getMethod("long nanoTime()");
  public static final Method ENABLED = Method.getMethod("boolean enabled()");
  public static final Method RECORD = new Method("record", "(J)V");
  public static final Type LATENCY_CLOCKED_TYPE =
      Type.getObjectType(LATENCY_CLOCKED_INTERNAL_NAME);
  public static final Type SYSTEM_TYPE = Type.getType(System.class);
  public static final Type TIMER_TYPE = Type.getObjectType(TIMER_INTERNAL_NAME);

  private AsmConstants() {}
}
