package golden;

import com.ll.metrics.latency.annotations.Timed;

public interface UnsupportedInterfaceAbstractTimedSample {
  @Timed
  void abstractInterfaceTimedMethod();
}
