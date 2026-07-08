package golden;

import com.ll.metrics.latency.annotations.Timed;

public abstract class UnsupportedAbstractTimedSample {
  @Timed
  public abstract void abstractTimedMethod();
}
