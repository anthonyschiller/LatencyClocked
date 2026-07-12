package com.ll.metrics.latency.example;

import com.ll.metrics.latency.annotations.Timed;

/** Example service with instance, static, and overloaded timed methods. */
public final class PricingService {
  /** Creates an example pricing service. */
  public PricingService() {}

  /**
   * Calculates a price for an order.
   *
   * @param order example order
   * @return calculated price
   */
  @Timed
  public long price(Order order) {
    return 100L * order.quantity();
  }

  /**
   * Calculates a price for a SKU.
   *
   * @param sku stock-keeping unit
   * @return calculated price
   */
  @Timed
  public long price(String sku) {
    return sku.length() * 10L;
  }

  /**
   * Validates an order using a timed static method.
   *
   * @param order example order
   * @return whether the order is valid
   */
  @Timed
  public static boolean validate(Order order) {
    return order.quantity() > 0 && !order.sku().isBlank();
  }
}
