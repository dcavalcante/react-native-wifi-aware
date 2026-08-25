package com.wifiaware

/** Main-thread-only data-path bookkeeping; native resources remain in WifiAwareModule. */
internal class DataPathRegistry<R> {
  enum class HandleState { LIVE, CLOSED, UNKNOWN }

  data class DataPath<R>(val discoveryHandle: String, val value: R)

  private val paths = mutableMapOf<String, DataPath<R>>()
  private val pending = mutableMapOf<String, String>()
  private val closed = mutableSetOf<String>()

  fun begin(handle: String, discoveryHandle: String) {
    pending[handle] = discoveryHandle
  }

  fun complete(handle: String, value: R): Boolean {
    val discoveryHandle = pending.remove(handle) ?: return false
    paths[handle] = DataPath(discoveryHandle, value)
    return true
  }

  fun path(handle: String): DataPath<R>? = paths[handle]

  fun state(handle: String): HandleState = when {
    paths.containsKey(handle) -> HandleState.LIVE
    closed.contains(handle) -> HandleState.CLOSED
    else -> HandleState.UNKNOWN
  }

  fun handlesForDiscovery(discoveryHandle: String): List<String> =
    paths.filterValues { it.discoveryHandle == discoveryHandle }.keys.toList() +
      pending.filterValues { it == discoveryHandle }.keys.toList()

  fun handles(): List<String> = paths.keys.toList() + pending.keys.toList()

  fun close(handle: String): DataPath<R>? {
    pending.remove(handle)
    closed.add(handle)
    return paths.remove(handle)
  }

  fun invalidate(): List<DataPath<R>> {
    val current = paths.values.toList()
    closed.addAll(paths.keys)
    closed.addAll(pending.keys)
    paths.clear()
    pending.clear()
    return current
  }
}
