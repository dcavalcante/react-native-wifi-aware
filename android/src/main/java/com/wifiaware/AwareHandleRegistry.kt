package com.wifiaware

/** Main-thread-only handle bookkeeping; native objects remain in WifiAwareModule. */
internal class AwareHandleRegistry<S, D> {
  enum class HandleState { LIVE, CLOSED, UNKNOWN }

  data class Discovery<D>(val parent: String, val value: D)

  private val sessions = mutableMapOf<String, S>()
  private val discoveries = mutableMapOf<String, Discovery<D>>()
  private val pendingSessions = mutableSetOf<String>()
  private val pendingDiscoveries = mutableMapOf<String, String>()
  private val closedSessions = mutableSetOf<String>()
  private val closedDiscoveries = mutableSetOf<String>()

  fun beginSession(handle: String) = pendingSessions.add(handle)
  fun completeSession(handle: String, value: S): Boolean {
    if (!pendingSessions.remove(handle)) return false
    sessions[handle] = value
    return true
  }
  fun session(handle: String): S? = sessions[handle]
  fun sessionState(handle: String): HandleState = when {
    sessions.containsKey(handle) -> HandleState.LIVE
    closedSessions.contains(handle) -> HandleState.CLOSED
    else -> HandleState.UNKNOWN
  }

  fun beginDiscovery(handle: String, parent: String) { pendingDiscoveries[handle] = parent }
  fun completeDiscovery(handle: String, value: D): Boolean {
    val parent = pendingDiscoveries.remove(handle) ?: return false
    if (!sessions.containsKey(parent)) {
      closedDiscoveries.add(handle)
      return false
    }
    discoveries[handle] = Discovery(parent, value)
    return true
  }
  fun discovery(handle: String): Discovery<D>? = discoveries[handle]
  fun discoveryState(handle: String): HandleState = when {
    discoveries.containsKey(handle) -> HandleState.LIVE
    closedDiscoveries.contains(handle) -> HandleState.CLOSED
    else -> HandleState.UNKNOWN
  }
  fun closeDiscovery(handle: String): Discovery<D>? {
    pendingDiscoveries.remove(handle)
    closedDiscoveries.add(handle)
    return discoveries.remove(handle)
  }
  fun discoveryHandlesForSession(parent: String): List<String> =
    discoveries.filterValues { it.parent == parent }.keys.toList() +
      pendingDiscoveries.filterValues { it == parent }.keys.toList()
  fun closeSession(handle: String): Pair<S?, List<Discovery<D>>> {
    pendingSessions.remove(handle)
    closedSessions.add(handle)
    val childHandles = discoveries.filterValues { it.parent == handle }.keys.toList()
    val children = childHandles.mapNotNull { closeDiscovery(it) }
    pendingDiscoveries.filterValues { it == handle }.keys.toList().forEach { closeDiscovery(it) }
    return sessions.remove(handle) to children
  }
  fun invalidate(): Pair<List<S>, List<Discovery<D>>> {
    val currentSessions = sessions.values.toList()
    val currentDiscoveries = discoveries.values.toList()
    closedSessions.addAll(sessions.keys); closedSessions.addAll(pendingSessions)
    closedDiscoveries.addAll(discoveries.keys); closedDiscoveries.addAll(pendingDiscoveries.keys)
    sessions.clear(); discoveries.clear(); pendingSessions.clear(); pendingDiscoveries.clear()
    return currentSessions to currentDiscoveries
  }
}
