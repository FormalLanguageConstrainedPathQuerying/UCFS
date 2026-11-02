package sppf

import org.junit.jupiter.api.Test
import org.ucfs.rsm.RsmState
import org.ucfs.rsm.symbol.Nonterminal
import org.ucfs.sppf.node.InputRange
import org.ucfs.sppf.node.ReachabilityRangeSppfNode
import org.ucfs.sppf.node.RsmRange
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReachabilityRangeHashTest {
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
