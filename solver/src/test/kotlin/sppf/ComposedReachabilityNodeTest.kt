package sppf

import org.junit.jupiter.api.Test
import org.ucfs.rsm.RsmState
import org.ucfs.rsm.symbol.Nonterminal
import org.ucfs.rsm.symbol.Term
import org.ucfs.sppf.ReachabilitySppfStorage
import org.ucfs.sppf.SppfStorage
import org.ucfs.sppf.node.InputRange
import org.ucfs.sppf.node.NonterminalType
import org.ucfs.sppf.node.RsmRange
import org.ucfs.sppf.node.getEmptyRange
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ComposedReachabilityNodeTest {
    private val nt = Nonterminal("S")
    private val s0 = RsmState(nt)
    private val s1 = RsmState(nt)
    private val s2 = RsmState(nt)

    @Test
    fun nonemptyLeftInternsOnlyComposedRange() {
        val storage = ReachabilitySppfStorage<Int>()
        val left = storage.addNode(InputRange(1, 2), RsmRange(s0, s1), Term("a"))

        val first = storage.addComposedNonterminalNode(left, InputRange(2, 3), RsmRange(s1, s2), s0, left)
        val again = storage.addComposedNonterminalNode(left, InputRange(2, 3), RsmRange(s1, s2), s0, left)

        assertSame(first, again)
        assertEquals(InputRange(1, 3), first.inputRange)
        assertEquals(RsmRange(s0, s2), first.rsmRange)
        assertEquals(2, internedCount(storage))
    }

    @Test
    fun emptyLeftUsesRightCoordinates() {
        val storage = ReachabilitySppfStorage<Int>()
        val result = storage.addComposedNonterminalNode(getEmptyRange(), InputRange(2, 3), RsmRange(s1, s2), s0, null)

        assertEquals(InputRange(2, 3), result.inputRange)
        assertEquals(RsmRange(s1, s2), result.rsmRange)
        assertEquals(1, internedCount(storage))
    }

    @Test
    fun fullSppfKeepsBothDerivationChildren() {
        val storage = SppfStorage<Int>()
        val left = storage.addNode(InputRange(1, 2), RsmRange(s0, s1), Term("a"))
        val child = storage.addNode(InputRange(2, 3), RsmRange(s1, s2), Term("b"))

        val composed = storage.addComposedNonterminalNode(
            left, InputRange(2, 3), RsmRange(s1, s2), s0, child
        )

        val intermediate = composed.children.single()
        val (retainedLeft, right) = intermediate.children.toList()
        assertSame(left, retainedLeft)
        val nonterminal = right.children.first { it.type is NonterminalType }
        assertSame(child, nonterminal.children.single())
    }

    @Test
    fun scalarCoordinatesPreserveReachabilityAndFullSppf() {
        val reach = ReachabilitySppfStorage<Int>()
        val full = SppfStorage<Int>()
        val reachLeft = reach.addNode(InputRange(1, 2), RsmRange(s0, s1), Term("a"))
        val fullLeft = full.addNode(InputRange(1, 2), RsmRange(s0, s1), Term("a"))
        val fullChild = full.addNode(InputRange(2, 3), RsmRange(s1, s2), Term("b"))

        val reachResult = reach.addComposedNonterminalNode(reachLeft, 2, 3, s1, s2, s0, null)
        val fullResult = full.addComposedNonterminalNode(fullLeft, 2, 3, s1, s2, s0, fullChild)

        assertEquals(InputRange(1, 3), reachResult.inputRange)
        assertEquals(RsmRange(s0, s2), reachResult.rsmRange)
        assertSame(fullLeft, fullResult.children.single().children.first())
        val right = fullResult.children.single().children.last()
        val nonterminal = right.children.first { it.type is NonterminalType }
        assertSame(fullChild, nonterminal.children.single())
    }

    private fun internedCount(storage: ReachabilitySppfStorage<Int>): Int {
        val field = SppfStorage::class.java.getDeclaredField("createdSppfNodes")
        field.isAccessible = true
        return (field.get(storage) as Map<*, *>).size
    }
}
