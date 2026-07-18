package com.ll.metrics.latency.core;

/** Management bean that exposes global LatencyClocked runtime controls. */
public final class LatencyClockedManagement implements LatencyClockedManagementBean {
  /** Creates the management bean. */
  public LatencyClockedManagement() {}

  @Override
  public boolean isEnabled() {
    return LatencyClocked.enabled();
  }

  @Override
  public void setEnabled(boolean enabled) {
    LatencyClocked.setEnabled(enabled);
  }
}
