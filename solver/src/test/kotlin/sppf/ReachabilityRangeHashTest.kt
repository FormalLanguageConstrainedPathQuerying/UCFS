package sppf

import org.junit.jupiter.api.Test
import org.ucfs.rsm.RsmState
import org.ucfs.rsm.symbol.Nonterminal
import org.ucfs.sppf.node.InputRange
import org.ucfs.sppf.node.ReachabilityRangeSppfNode
import org.ucfs.sppf.node.RsmRange
import org.ucfs.sppf.ReachabilitySppfStorage
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReachabilityRangeHashTest {
    @Test
    fun directCoordinatesKeepCompatibilityRangesAndCanonicalization() {
        val from = RsmState(Nonterminal("S"), numId = 1)
        val to = RsmState(Nonterminal("S"), numId = 2)
        val storage = ReachabilitySppfStorage<Int>()
        val first = storage.addNonterminalNode(InputRange(3, 5), RsmRange(from, to), from, null)
        val equal = storage.addNonterminalNode(InputRange(3, 5), RsmRange(from, to), from, null)

        assertSame(first, equal)
        assertEquals(3, first.inputFrom)
        assertEquals(5, first.inputTo)
        assertSame(from, first.rsmFrom)
        assertSame(to, first.rsmTo)
        assertEquals(InputRange(3, 5), first.inputRange)
        assertEquals(RsmRange(from, to), first.rsmRange)
        assertNotSame(first.inputRange, first.inputRange)
        assertNotSame(first.rsmRange, first.rsmRange)
        assertEquals(first.hashCode(), equal.hashCode())
    }

    @Test
    fun distributesCorrelatedIntegerRanges() {
        val state = RsmState(Nonterminal("S"))
        val range = RsmRange(state, state)
        val hashes = (0 until 256).map { i ->
            ReachabilityRangeSppfNode(InputRange(i shl 7, i), range).hashCode()
        }
        assertTrue(hashes.toSet().size >= 250, "256 distinct ranges gave only ${hashes.toSet().size} hashes")
    }

    @Test
    fun equalNullableRangesAndStructuralStatesHaveEqualHashes() {
        val firstState = RsmState(Nonterminal("S"), numId = 7)
        val secondState = RsmState(Nonterminal("S"), numId = 7)
        assertEquals(firstState, secondState)
        val first = ReachabilityRangeSppfNode(InputRange<String?>(null, "x"), RsmRange(firstState, firstState))
        val second = ReachabilityRangeSppfNode(InputRange<String?>(null, "x"), RsmRange(secondState, secondState))
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }
}
