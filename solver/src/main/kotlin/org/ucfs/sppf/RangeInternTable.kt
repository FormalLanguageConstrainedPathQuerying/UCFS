package org.ucfs.sppf

import org.ucfs.rsm.RsmState
import org.ucfs.sppf.node.ReachabilityRangeSppfNode

/** Open-addressed canonical table for reach-only ranges; lookup does not allocate a probe node. */
internal class RangeInternTable<VertexType>(initialCapacity: Int = 16) {
    init {
        require(initialCapacity >= 4 && initialCapacity <= MAX_CAPACITY &&
                initialCapacity and (initialCapacity - 1) == 0)
    }

    private var slots: Array<ReachabilityRangeSppfNode<VertexType>?> = arrayOfNulls(initialCapacity)
    private var size = 0
    private var threshold = initialCapacity / 2

    fun intern(
        inputFrom: VertexType, inputTo: VertexType, rsmFrom: RsmState, rsmTo: RsmState
    ): ReachabilityRangeSppfNode<VertexType> {
        val hash = ReachabilityRangeSppfNode.coordinateHash(inputFrom, inputTo, rsmFrom, rsmTo)
        var index = spread(hash) and (slots.size - 1)
        while (true) {
            val existing = slots[index] ?: break
            if (existing.hashCode() == hash && existing.inputFrom == inputFrom && existing.inputTo == inputTo &&
                    existing.rsmFrom == rsmFrom && existing.rsmTo == rsmTo) return existing
            index = (index + 1) and (slots.size - 1)
        }
        val node = ReachabilityRangeSppfNode.withHash(inputFrom, inputTo, rsmFrom, rsmTo, hash)
        if (size + 1 > threshold) {
            grow()
            index = findEmpty(hash, slots)
        }
        slots[index] = node
        size++
        return node
    }

    private fun grow() {
        check(slots.size < MAX_CAPACITY) { "RangeInternTable exceeds maximum capacity $MAX_CAPACITY" }
        val expanded = arrayOfNulls<ReachabilityRangeSppfNode<VertexType>>(slots.size * 2)
        for (entry in slots) if (entry != null) expanded[findEmpty(entry.hashCode(), expanded)] = entry
        slots = expanded
        threshold = slots.size / 2
    }

    private fun findEmpty(hash: Int, target: Array<ReachabilityRangeSppfNode<VertexType>?>): Int {
        var index = spread(hash) and (target.size - 1)
        while (target[index] != null) index = (index + 1) and (target.size - 1)
        return index
    }

    private fun spread(hash: Int): Int = hash xor (hash ushr 16)

    companion object {
        private const val MAX_CAPACITY = 1 shl 29
    }
}
