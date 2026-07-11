package com.example.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.one.ModuleOneService;
import com.example.two.ModuleTwoService;
import com.ll.metrics.latency.core.LatencyClocked;
import com.ll.metrics.latency.timer.InMemoryTimers;
import com.ll.metrics.latency.timer.Timers;
import org.junit.jupiter.api.Test;

final class MultiModuleRuntimeTest {
  @Test
  void loadsIndexesFromBothModulesAndRecords() {
    Timers timers = InMemoryTimers.create();
    LatencyClocked.initialise(timers);

    assertEquals(1, new ModuleOneService().one());
    assertEquals(2, ModuleTwoService.two());

    assertEquals(1, timers.timer("it.module.one").snapshot().count());
    assertEquals(1, timers.timer("it.module.two").snapshot().count());
  }
}
