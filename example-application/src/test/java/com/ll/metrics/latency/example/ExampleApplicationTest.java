package com.ll.metrics.latency.example;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ll.metrics.latency.core.LatencyClocked;
import com.ll.metrics.latency.hdr.HdrTimers;
import com.ll.metrics.latency.snapshot.TimerSnapshot;
import com.ll.metrics.latency.timer.Timers;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ExampleApplicationTest {
  @Test
  void exampleRecordsInstanceStaticAndOverloadedMethods() {
    final LatencyClocked lc = LatencyClocked.initialise();
    PricingService pricingService = new PricingService();
    Order order = new Order("ABC-123", 2);

    assertEquals(200, pricingService.price(order));
    assertEquals(70, pricingService.price(order.sku()));
    assertTrue(PricingService.validate(order));

    Map<String, TimerSnapshot> snapshots =
        lc.snapshots().stream()
            .collect(Collectors.toMap(TimerSnapshot::id, Function.identity()));
    assertEquals(
        1, snapshots.get(methodId("price", "(Lcom/ll/metrics/latency/example/Order;)J")).count());
    assertEquals(1, snapshots.get(methodId("price", "(Ljava/lang/String;)J")).count());
    assertEquals(
        1,
        snapshots
            .get(methodId("validate", "(Lcom/ll/metrics/latency/example/Order;)Z"))
            .count());
  }

  @Test
  void mainRunsEndToEnd() {
    PrintStream previousOut = System.out;
    try (PrintStream replacementOut = new PrintStream(new ByteArrayOutputStream())) {
      System.setOut(replacementOut);
      assertDoesNotThrow(() -> ExampleApplication.main(new String[0]));
    } finally {
      System.setOut(previousOut);
    }
  }

  private static String methodId(String methodName, String descriptor) {
    return PricingService.class.getName() + "#" + methodName + descriptor;
  }
}
