package com.wifiaware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AwareHandleRegistryTest {
  @Test fun `late session callback is discarded after invalidation`() {
    val registry = AwareHandleRegistry<String, String>()
    registry.beginSession("session:1")
    registry.invalidate()
    assertFalse(registry.completeSession("session:1", "native-session"))
    assertEquals(AwareHandleRegistry.HandleState.CLOSED, registry.sessionState("session:1"))
  }

  @Test fun `closing parent closes live and pending discoveries`() {
    val registry = AwareHandleRegistry<String, String>()
    registry.beginSession("session:1"); registry.completeSession("session:1", "native-session")
    registry.beginDiscovery("discovery:1", "session:1"); registry.completeDiscovery("discovery:1", "native-discovery")
    registry.beginDiscovery("discovery:2", "session:1")
    val (_, discoveries) = registry.closeSession("session:1")
    assertEquals(listOf("native-discovery"), discoveries.map { it.value })
    assertEquals(AwareHandleRegistry.HandleState.CLOSED, registry.discoveryState("discovery:1"))
    assertEquals(AwareHandleRegistry.HandleState.CLOSED, registry.discoveryState("discovery:2"))
    assertNull(registry.discovery("discovery:1"))
  }
}
