package com.theveloper.pixelplay.utils

/** Small synchronized LRU for app-lifetime caches. Snapshots never expose the mutable map. */
internal class BoundedCache<K, V>(private val capacity: Int) {
    init { require(capacity > 0) }
    private val entries = LinkedHashMap<K, V>(16, 0.75f, true)

    @Synchronized operator fun get(key: K): V? = entries[key]

    @Synchronized operator fun set(key: K, value: V) {
        entries[key] = value
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }

    @Synchronized fun remove(key: K): V? = entries.remove(key)
    @Synchronized fun clear() = entries.clear()
    @Synchronized fun snapshot(): Map<K, V> = LinkedHashMap(entries)
    @Synchronized fun removeIf(predicate: (V) -> Boolean) {
        entries.entries.removeAll { predicate(it.value) }
    }
}
