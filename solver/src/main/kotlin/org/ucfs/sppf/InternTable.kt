package org.ucfs.sppf

/** Canonicalizes immutable, non-null values without allocating one map entry object per value. */
internal class InternTable<T : Any>(initialCapacity: Int = 16) {
    init {
        require(initialCapacity >= 4 && initialCapacity <= MAX_CAPACITY &&
                initialCapacity and (initialCapacity - 1) == 0) {
            "initialCapacity must be a power of two between 4 and $MAX_CAPACITY"
        }
    }

    private var slots: Array<Any?> = arrayOfNulls(initialCapacity)
    private var size = 0
    private var threshold = threshold(initialCapacity)

    @Suppress("UNCHECKED_CAST")
    fun intern(value: T): T {
        var index = spread(value.hashCode()) and (slots.size - 1)
        while (true) {
            val existing = slots[index] ?: break
            if (existing == value) return existing as T
            index = (index + 1) and (slots.size - 1)
        }

        if (size + 1 > threshold) {
            grow()
            index = findEmpty(value, slots)
        }
        slots[index] = value
        size++
        return value
    }

    private fun grow() {
        check(slots.size < MAX_CAPACITY) { "InternTable exceeds maximum capacity $MAX_CAPACITY" }
        val expanded = arrayOfNulls<Any>(slots.size * 2)
        for (entry in slots) if (entry != null) expanded[findEmpty(entry, expanded)] = entry
        slots = expanded
        threshold = threshold(slots.size)
    }

    private fun findEmpty(value: Any, target: Array<Any?>): Int {
        var index = spread(value.hashCode()) and (target.size - 1)
        while (target[index] != null) index = (index + 1) and (target.size - 1)
        return index
    }

    private fun spread(hash: Int): Int = hash xor (hash ushr 16)

    private fun threshold(capacity: Int): Int = (capacity.toLong() * 2 / 3).toInt()

    companion object {
        private const val MAX_CAPACITY = 1 shl 29
    }
}
