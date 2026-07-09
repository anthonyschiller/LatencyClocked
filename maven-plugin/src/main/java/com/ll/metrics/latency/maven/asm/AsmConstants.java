package com.ll.metrics.latency.maven.asm;

import com.ll.metrics.latency.constants.LatencyClockedConstants;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

final class AsmConstants {
  static final String TIMER_DESCRIPTOR = "Lcom/ll/metrics/latency/timer/Timer;";
  static final String TIMER_INTERNAL_NAME = "com/ll/metrics/latency/timer/Timer";
  static final String TIMERS_INTERNAL_NAME = "com/ll/metrics/latency/timer/Timers";
  static final String TIMERS_DESCRIPTOR = "L" + TIMERS_INTERNAL_NAME + ";";
  static final String BIND_METHOD_NAME = LatencyClockedConstants.BIND_METHOD;
  static final String BIND_METHOD_DESCRIPTOR = "(" + TIMERS_DESCRIPTOR + ")V";
  static final Method NANO_TIME = Method.getMethod("long nanoTime()");
  static final Method RECORD = new Method("record", "(J)V");
  static final Type SYSTEM_TYPE = Type.getType(System.class);
  static final Type TIMER_TYPE = Type.getObjectType(TIMER_INTERNAL_NAME);

  private AsmConstants() {}
}
