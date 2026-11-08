package parser

import org.junit.jupiter.api.Test
import org.ucfs.descriptors.Descriptor
import org.ucfs.descriptors.DescriptorsStorage
import org.ucfs.input.InputGraph
import org.ucfs.input.TerminalInputLabel
import org.ucfs.parser.Gll
import org.ucfs.rsm.RsmState
import org.ucfs.rsm.symbol.Nonterminal
import org.ucfs.rsm.symbol.Term
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReturnLookaheadTest {
    @Test
    fun reachabilitySkipsDeadTerminalContinuationAfterPop() {
        val s = Nonterminal("S")
        val a = Nonterminal("A")
        val root = Nonterminal("root")
        val s0 = RsmState(s, isStart = true)
        val afterA = RsmState(s)
        val sFinal = RsmState(s, isFinal = true)
        val a0 = RsmState(a, isStart = true)
        val aFinal = RsmState(a, isFinal = true)
        val root0 = RsmState(root, isStart = true)
        val rootFinal = RsmState(root, isFinal = true)
        s.startState = s0; a.startState = a0; root.startState = root0
        val ta = Term("a"); val tb = Term("b")
        s0.addEdge(a, afterA); afterA.addEdge(tb, sFinal)
        a0.addEdge(ta, aFinal)
        root0.addEdge(s, rootFinal)

        val graph = InputGraph<Int, TerminalInputLabel>()
        for (v in 0..3) graph.addStartVertex(v)
        graph.addEdge(0, TerminalInputLabel(ta), 1)
        graph.addEdge(0, TerminalInputLabel(ta), 2)
        graph.addEdge(1, TerminalInputLabel(tb), 3)

        val full = Gll.gll(root0, graph)
        val reach = Gll.gll(root0, graph, reachabilityOnly = true)
        val expected = setOf(0 to 3)
        assertEquals(expected, full.parse().map { it.inputRange!!.from to it.inputRange!!.to }.toSet())
        assertEquals(expected, reach.parse().map { it.inputRange!!.from to it.inputRange!!.to }.toSet())
        assertTrue(handled(full).any { it.rsmState === afterA && it.inputPosition == 2 })
        assertFalse(handled(reach).any { it.rsmState === afterA && it.inputPosition == 2 })
    }

    private fun handled(gll: Gll<Int, TerminalInputLabel>): Set<Descriptor<Int>> {
        val field = DescriptorsStorage::class.java.getDeclaredField("handledDescriptors")
        field.isAccessible = true
        val table = field.get(gll.ctx.descriptors)
        val slotsField = table.javaClass.getDeclaredField("slots")
        slotsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return (slotsField.get(table) as Array<*>).filterNotNull().map { it as Descriptor<Int> }.toSet()
    }
}
