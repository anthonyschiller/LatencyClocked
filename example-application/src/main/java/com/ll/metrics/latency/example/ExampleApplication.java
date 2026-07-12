package com.ll.metrics.latency.example;

import com.ll.metrics.latency.core.LatencyClocked;

/** Framework-independent example application. */
public final class ExampleApplication {
  private ExampleApplication() {}

  /**
   * Runs the example application.
   *
   * @param args ignored command-line arguments
   */
  public static void main(String[] args) {
    final LatencyClocked latencyClocked = LatencyClocked.initialise();

    PricingService pricingService = new PricingService();
    Order order = new Order("ABC-123", 2);

    pricingService.price(order);
    pricingService.price(order.sku());
    PricingService.validate(order);

    latencyClocked.snapshots().forEach(System.out::println);
  }
}
