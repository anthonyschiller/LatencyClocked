package golden;

import com.ll.metrics.latency.annotations.Timed;

public interface UnsupportedDefaultInterfaceTimedSample {
  @Timed
  default int defaultTimedMethod(int value) {
    return value + 1;
  }
}
