package com.wifiaware

/** Main-thread-only pending follow-up messages keyed by an internal Android message ID. */
internal class PendingMessageRegistry<P> {
  data class Pending<P>(val discoveryHandle: String, val value: P)

  private val pending = mutableMapOf<Int, Pending<P>>()
  private var nextMessageId = 1

  fun begin(discoveryHandle: String, value: P): Int {
    while (pending.containsKey(nextMessageId)) advance()
    val messageId = nextMessageId
    advance()
    pending[messageId] = Pending(discoveryHandle, value)
    return messageId
  }

  fun complete(discoveryHandle: String, messageId: Int): Pending<P>? {
    val value = pending[messageId] ?: return null
    if (value.discoveryHandle != discoveryHandle) return null
    return pending.remove(messageId)
  }

  fun removeForDiscovery(discoveryHandle: String): List<Pending<P>> =
    pending.filterValues { it.discoveryHandle == discoveryHandle }.keys.toList().mapNotNull(pending::remove)

  fun invalidate(): List<Pending<P>> = pending.values.toList().also { pending.clear() }

  private fun advance() {
    nextMessageId = if (nextMessageId == Int.MAX_VALUE) 1 else nextMessageId + 1
  }
}
