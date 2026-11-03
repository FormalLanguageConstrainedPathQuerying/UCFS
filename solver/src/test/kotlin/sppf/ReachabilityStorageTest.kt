package sppf

import org.junit.jupiter.api.Test
import org.ucfs.rsm.RsmState
import org.ucfs.rsm.symbol.Nonterminal
import org.ucfs.rsm.symbol.Term
import org.ucfs.sppf.ReachabilitySppfStorage
import org.ucfs.sppf.SppfStorage
import org.ucfs.sppf.node.InputRange
import org.ucfs.sppf.node.RsmRange
import org.ucfs.sppf.node.getEmptyRange
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReachabilityStorageTest {
    @Test
    fun reachabilityOperationsCanonicalizeTheSameRangeWithoutChildren() {
        val start = RsmState(Nonterminal("S"), numId = 0)
        val middle = RsmState(Nonterminal("S"), numId = 1)
        val end = RsmState(Nonterminal("S"), numId = 2)
        val storage = ReachabilitySppfStorage<Int>()
        val firstRange = InputRange(1, 2)
        val firstRsm = RsmRange(start, middle)

        val terminal = storage.addNode(firstRange, firstRsm, Term("a"))
        assertSame(terminal, storage.addNode(InputRange(1, 2), RsmRange(start, middle), Term("a")))
        assertSame(terminal, storage.addNonterminalNode(firstRange, firstRsm, start, terminal))
        assertSame(terminal, storage.addEpsilonNode(firstRange, firstRsm, start))
        assertTrue(terminal.children.none())
        assertSame(terminal, storage.addIntermediateNode(getEmptyRange(), terminal))

        val right = storage.addNode(InputRange(2, 3), RsmRange(middle, end), Term("b"))
        val combined = storage.addIntermediateNode(terminal, right)
        assertSame(combined, storage.addNonterminalNode(InputRange(1, 3), RsmRange(start, end), start))
        assertTrue(combined.children.none())

        val full = SppfStorage<Int>().addNode(firstRange, firstRsm, Term("a"))
        assertTrue(full.children.any())
    }
}
