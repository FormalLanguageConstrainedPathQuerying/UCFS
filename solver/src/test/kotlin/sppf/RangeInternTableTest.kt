package sppf

import org.junit.jupiter.api.Test
import org.ucfs.rsm.RsmState
import org.ucfs.rsm.symbol.Nonterminal
import org.ucfs.sppf.RangeInternTable
import org.ucfs.sppf.node.ReachabilityRangeSppfNode
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class RangeInternTableTest {
    private class CountedVertex(val id: Int) {
        var hashCalls = 0
        override fun hashCode(): Int { hashCalls++; return id }
        override fun equals(other: Any?): Boolean = other is CountedVertex && id == other.id
    }

    private class CollidingVertex(val id: Int) {
        override fun hashCode(): Int = 1
        override fun equals(other: Any?): Boolean = other is CollidingVertex && id == other.id
    }

    private class EqualsCounterVertex(val id: Int) {
        var equalsCalls = 0
        override fun hashCode(): Int = id
        override fun equals(other: Any?): Boolean {
            equalsCalls++
            return other is EqualsCounterVertex && id == other.id
        }
    }

    @Test
    fun skipsCoordinateEqualityForDifferentHashesAtSameSlot() {
        val start = RsmState(Nonterminal("S"), numId = 0)
        val end = RsmState(Nonterminal("S"), numId = 1)
        val first = EqualsCounterVertex(1)
        val firstHash = ReachabilityRangeSppfNode.coordinateHash(first, 0, start, end)
        fun slot(hash: Int): Int = (hash xor (hash ushr 16)) and 3
        val second = (2..1000).map(::EqualsCounterVertex).first { vertex ->
            val hash = ReachabilityRangeSppfNode.coordinateHash(vertex, 0, start, end)
            hash != firstHash && slot(hash) == slot(firstHash)
        }
        val table = RangeInternTable<Any>(initialCapacity = 4)

        table.intern(first, 0, start, end)
        table.intern(second, 0, start, end)
        assertEquals(0, first.equalsCalls)
    }

    @Test
    fun canonicalizesByFourCoordinatesWithoutHashingTwiceOnMiss() {
        val start = RsmState(Nonterminal("S"), numId = 0)
        val end = RsmState(Nonterminal("S"), numId = 1)
        val from = CountedVertex(1)
        val to = CountedVertex(2)
        val table = RangeInternTable<CountedVertex>()

        val first = table.intern(from, to, start, end)
        assertEquals(1, from.hashCalls)
        assertEquals(1, to.hashCalls)
        assertSame(first, table.intern(CountedVertex(1), CountedVertex(2), start, end))
        assertNotSame(first, table.intern(from, to, end, start))
        assertEquals(ReachabilityRangeSppfNode(from, to, start, end).hashCode(), first.hashCode())
    }

    @Test
    fun preservesCanonicalNodesAcrossCollisionsAndGrowthWithNullableVertices() {
        val start = RsmState(Nonterminal("S"), numId = 0)
        val end = RsmState(Nonterminal("S"), numId = 1)
        val table = RangeInternTable<CollidingVertex?>()
        val nodes = (0 until 100).map { id ->
            table.intern(CollidingVertex(id), null, start, end)
        }
        for (id in nodes.indices) {
            assertSame(nodes[id], table.intern(CollidingVertex(id), null, start, end))
        }
        assertNotSame(nodes[0], table.intern(null, CollidingVertex(0), start, end))
    }
}
