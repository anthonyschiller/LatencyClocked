package golden;

import com.ll.metrics.latency.annotations.Timed;
import com.ll.metrics.latency.constants.LatencyClockedConstants;
import com.ll.metrics.latency.core.LatencyClocked;
import java.lang.management.ManagementFactory;
import javax.management.Attribute;
import javax.management.ObjectName;

public final class GoldenTimedSamples {
  public int sideEffect;

  @Timed
  public void successfulVoid() {
    sideEffect++;
  }

  @Timed
  public int primitiveReturn(int value) {
    return value + 1;
  }

  @Timed
  public long longReturn(long value) {
    return value + 1L;
  }

  @Timed
  public float floatReturn(float value) {
    return value + 1.0f;
  }

  @Timed
  public double doubleReturn(double value) {
    return value + 1.0d;
  }

  @Timed
  public String objectReturn(String value) {
    return "object:" + value;
  }

  @Timed
  public static int staticPrimitive(int value) {
    return value * 2;
  }

  @Timed
  public int multipleReturnPaths(int value) {
    if (value < 0) {
      return -1;
    }
    if (value == 0) {
      return 0;
    }
    return 1;
  }

  @Timed
  public int nestedBranching(int value) {
    if (value < 0) {
      if (value < -10) {
        sideEffect += 100;
        return -100;
      }
      sideEffect += 10;
      return -10;
    }
    if (value == 0) {
      sideEffect += 1;
      return 0;
    }
    sideEffect += 2;
    return 10;
  }

  @Timed
  public String switchStatement(int value) {
    switch (value) {
      case 0:
        return "zero";
      case 1:
      case 2:
        return "small";
      default:
        return "large";
    }
  }

  @Timed
  public int loopWithEarlyReturn(int limit) {
    for (int index = 0; index < limit; index++) {
      sideEffect++;
      if (index == 2) {
        return index;
      }
    }
    return -1;
  }

  @Timed
  public int tryCatchReturningFromCatch(boolean fail) {
    try {
      if (fail) {
        throw new IllegalArgumentException("catch me");
      }
      return 10;
    } catch (IllegalArgumentException exception) {
      sideEffect += 3;
      return 20;
    }
  }

  @Timed
  public int tryFinallyReturningNormally(int value) {
    try {
      return value + 1;
    } finally {
      sideEffect += 4;
    }
  }

  @Timed
  public void throwingMethod() {
    throw new IllegalStateException("boom");
  }

  @Timed
  public int maybeThrow(boolean fail) {
    if (fail) {
      throw new IllegalStateException("boom");
    }
    return 42;
  }

  @Timed
  public int exceptionThrownIfValueUnhandled(int value) {
    if (value < 0) {
      return -1;
    }
    if (value == 0) {
      return 0;
    }
    throw new IllegalStateException("unhandled value");
  }

  @Timed
  public int catchesInternally(boolean fail) {
    try {
      if (fail) {
        throw new IllegalStateException("boom");
      }
      return 1;
    } catch (IllegalStateException exception) {
      return 2;
    }
  }

  @Timed
  public int exceptionThrownBeforeReturn() {
    sideEffect += 5;
    throw new IllegalStateException("before return");
  }

  @Timed
  public int exceptionThrownInsideCatch() {
    try {
      throw new IllegalArgumentException("original");
    } catch (IllegalArgumentException exception) {
      sideEffect += 6;
      throw new IllegalStateException("inside catch", exception);
    }
  }

  public int callPrivateTimedMethod(int value) {
    return privateTimedMethod(value);
  }

  @Timed
  private int privateTimedMethod(int value) {
    sideEffect += 7;
    return value + 7;
  }

  @Timed
  public final int finalTimedMethod(int value) {
    return value + 8;
  }

  @Timed
  public synchronized int synchronizedTimedMethod(int value) {
    sideEffect += 9;
    return value + 9;
  }

  @Timed
  public void toggleEnabled() {
    try {
      ManagementFactory.getPlatformMBeanServer()
          .setAttribute(
              new ObjectName(LatencyClockedConstants.MBEAN_NAME),
              new Attribute("Enabled", !LatencyClocked.enabled()));
    } catch (javax.management.JMException e) {
      throw new IllegalStateException(e);
    }
  }

  @Timed
  public int overloaded(int value) {
    return value + 10;
  }

  @Timed
  public String overloaded(String value) {
    return "overloaded:" + value;
  }

  public void normalMethod() {
    sideEffect += 10;
  }
}
