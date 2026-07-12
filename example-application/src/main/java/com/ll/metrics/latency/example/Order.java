package com.ll.metrics.latency.example;

/**
 * Example order input.
 *
 * @param sku stock-keeping unit
 * @param quantity requested quantity
 */
public record Order(String sku, int quantity) {}
