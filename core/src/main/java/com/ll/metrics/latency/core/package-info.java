/**
 * Stable runtime startup API.
 *
 * <p>Applications call {@link com.ll.metrics.latency.core.LatencyClocked#initialise()} or related
 * overloads once during startup to bind generated timer fields and obtain a snapshot-reading
 * runtime handle. Timed method execution uses no runtime proxies and no hot-path reflection after
 * startup.
 */
package com.ll.metrics.latency.core;
