package golden;

import com.ll.metrics.latency.annotations.Timed;

public class UnsupportedNativeTimedSample {
  @Timed
  public native void nativeTimedMethod();
}
