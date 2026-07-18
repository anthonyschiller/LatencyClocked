package com.ll.metrics.latency.core;

import javax.management.MXBean;

/** JMX management view for the global LatencyClocked runtime controls. */
@MXBean
public interface LatencyClockedManagementBean {
  /**
   * Returns whether generated latency recording is enabled.
   *
   * @return true when generated timing code should record successful invocations
   */
  boolean isEnabled();

  /**
   * Enables or disables generated latency recording.
   *
   * @param enabled true to enable recording, false to discard timed invocations
   */
  void setEnabled(boolean enabled);
}
