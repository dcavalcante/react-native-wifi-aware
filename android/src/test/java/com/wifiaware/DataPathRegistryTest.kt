package com.wifiaware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DataPathRegistryTest {
  @Test fun `close is idempotent and records the closed handle`() {
    val registry = DataPathRegistry<String>()
    registry.begin("data-path:1", "discovery:1")
    assertEquals(DataPathRegistry.HandleState.UNKNOWN, registry.state("data-path:1"))
    assertEquals(listOf("data-path:1"), registry.handlesForDiscovery("discovery:1"))
    registry.close("data-path:1")
    assertEquals(DataPathRegistry.HandleState.CLOSED, registry.state("data-path:1"))
    assertNull(registry.close("data-path:1"))
  }

  @Test fun `discovery close can retire live data paths`() {
    val registry = DataPathRegistry<String>()
    registry.begin("data-path:1", "discovery:1")
    assertEquals(true, registry.complete("data-path:1", "resources"))
    assertEquals(listOf("data-path:1"), registry.handlesForDiscovery("discovery:1"))
    assertEquals("resources", registry.close("data-path:1")?.value)
    assertEquals(DataPathRegistry.HandleState.CLOSED, registry.state("data-path:1"))
  }

  @Test fun `invalidation retires live and pending paths and discards late completion`() {
    val registry = DataPathRegistry<String>()
    registry.begin("data-path:1", "discovery:1")
    registry.complete("data-path:1", "first")
    registry.begin("data-path:2", "discovery:2")
    assertEquals(listOf("first"), registry.invalidate().map { it.value })
    assertEquals(DataPathRegistry.HandleState.CLOSED, registry.state("data-path:1"))
    assertEquals(DataPathRegistry.HandleState.CLOSED, registry.state("data-path:2"))
    assertFalse(registry.complete("data-path:2", "late"))
  }

  @Test fun `availability cleanup can retire every path before invalidation`() {
    val registry = DataPathRegistry<String>()
    registry.begin("data-path:1", "discovery:1")
    registry.complete("data-path:1", "first")
    registry.begin("data-path:2", "discovery:2")

    assertEquals(listOf("data-path:1", "data-path:2"), registry.handles())
    registry.handles().forEach { registry.close(it) }

    assertEquals(DataPathRegistry.HandleState.CLOSED, registry.state("data-path:1"))
    assertEquals(DataPathRegistry.HandleState.CLOSED, registry.state("data-path:2"))
    assertEquals(emptyList<DataPathRegistry.DataPath<String>>(), registry.invalidate())
  }
}
