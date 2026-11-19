package com.nuvio.app.core.storage

/**
 * Session cache that keeps at most [maxEntries], evicting the oldest write first.
 * Reads never reorder entries, so read-only access stays as safe as on a plain map.
 */
internal class BoundedCacheMap<K, V>(
    private val maxEntries: Int,
    private val delegate: MutableMap<K, V> = LinkedHashMap(),
) : MutableMap<K, V> by delegate {
    init {
        require(maxEntries > 0)
    }

    override fun put(key: K, value: V): V? {
        val previous = delegate.remove(key)
        delegate[key] = value
        while (delegate.size > maxEntries) {
            val iterator = delegate.keys.iterator()
            iterator.next()
            iterator.remove()
        }
        return previous
    }

    override fun putAll(from: Map<out K, V>) {
        from.forEach { (key, value) -> put(key, value) }
    }
}
