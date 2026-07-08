package golden;

import com.ll.metrics.latency.annotations.Timed;

public final class GenericBridgeTimedSamples {
  interface GenericHandler<T> {
    T handle(T value);
  }

  static final class StringHandler implements GenericHandler<String> {
    @Override
    @Timed
    public String handle(String value) {
      return "handled:" + value;
    }
  }
}
