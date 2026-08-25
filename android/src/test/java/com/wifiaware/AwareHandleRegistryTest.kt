package com.wifiaware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
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

  @Test fun `pending messages settle once and are removed with discovery`() {
    val messages = PendingMessageRegistry<String>()
    val first = messages.begin("discovery:1", "first")
    val second = messages.begin("discovery:1", "second")
    assertNotEquals(first, second)
    assertNull(messages.complete("discovery:2", first))
    assertEquals("first", messages.complete("discovery:1", first)?.value)
    assertNull(messages.complete("discovery:1", first))
    assertEquals(listOf("second"), messages.removeForDiscovery("discovery:1").map { it.value })
    assertNull(messages.complete("discovery:1", second))
  }

  @Test fun `invalidation clears all pending messages`() {
    val messages = PendingMessageRegistry<String>()
    messages.begin("discovery:1", "first")
    messages.begin("discovery:2", "second")
    assertEquals(listOf("first", "second"), messages.invalidate().map { it.value })
    assertEquals(emptyList<PendingMessageRegistry.Pending<String>>(), messages.invalidate())
  }
}
