package parser

import org.junit.jupiter.api.Test
import org.ucfs.input.InputGraph
import org.ucfs.input.TerminalInputLabel
import org.ucfs.parser.Gll
import org.ucfs.rsm.RsmState
import org.ucfs.rsm.symbol.Nonterminal
import org.ucfs.rsm.symbol.Term
import org.ucfs.sppf.node.InputRange
import org.ucfs.sppf.node.LeafSppfNode
import org.ucfs.sppf.node.Range
import org.ucfs.sppf.node.RsmRange
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Reachability-only mode: the parser keeps only the deduplicated range nodes of the SPPF (no value nodes,
 * no children). Descriptor identity is unchanged, so the parse results (input ranges) must be the same as with
 * the full SPPF.
 */
class ReachabilityOnlyTest {
    private val a = Term("a"); private val b = Term("b"); private val c = Term("c"); private val d = Term("d")

    /** S -> a S b S | c S d S | eps  (Dyck, two kinds), wrapped in a fictive start; returns the fictive start state */
    private fun dyck(): RsmState {
        val s = Nonterminal("S")
        val s0 = RsmState(s, isStart = true, isFinal = true); s.startState = s0
        for ((o, cl) in listOf(a to b, c to d)) {
            val q1 = RsmState(s); val q2 = RsmState(s); val q3 = RsmState(s); val q4 = RsmState(s, isFinal = true)
            s0.addEdge(o, q1); q1.addEdge(s, q2); q2.addEdge(cl, q3); q3.addEdge(s, q4)
        }
        return fictive(s)
    }

    /** P -> a P | P P | b  (ambiguous, left-recursive) */
    private fun ambiguous(): RsmState {
        val p = Nonterminal("P")
        val p0 = RsmState(p, isStart = true); p.startState = p0
        val pF = RsmState(p, isFinal = true); val p1 = RsmState(p); val p2 = RsmState(p)
        p0.addEdge(a, p1); p1.addEdge(p, pF); p0.addEdge(p, p2); p2.addEdge(p, pF); p0.addEdge(b, pF)
        return fictive(p)
    }

    private fun fictive(nt: Nonterminal): RsmState {
        val f = Nonterminal("fictive")
        val f0 = RsmState(f, isStart = true); val f1 = RsmState(f, isFinal = true); f.startState = f0
        f0.addEdge(nt, f1)
        return f0
    }

    private fun graph(rnd: Random, n: Int, m: Int, terms: List<Term<String>>): InputGraph<Int, TerminalInputLabel> {
        val g = InputGraph<Int, TerminalInputLabel>()
        val labels = terms.map { TerminalInputLabel(it) }
        for (v in 0 until n) { g.addVertex(v); g.addStartVertex(v) }
        repeat(m) { g.addEdge(rnd.nextInt(n), labels[rnd.nextInt(labels.size)], rnd.nextInt(n)) }
        return g
    }

    private fun pairs(start: RsmState, g: InputGraph<Int, TerminalInputLabel>, reachOnly: Boolean) =
        Gll.gll(start, g, reachabilityOnly = reachOnly).parse().map { it.inputRange!!.from to it.inputRange!!.to }.toSet()

    private fun compactPairs(start: RsmState, g: InputGraph<Int, TerminalInputLabel>) =
        Gll.gll(start, g, reachabilityOnly = true, compactReachability = true)
            .parse().map { it.inputRange!!.from to it.inputRange!!.to }.toSet()

    @Test
    fun sameResultsAsFullSppfOnRandomGraphs() {
        val rnd = Random(7)
        var nonEmpty = 0
        repeat(60) { i ->
            val (start, terms) = if (i % 2 == 0) dyck() to listOf(a, b, c, d) else ambiguous() to listOf(a, b)
            val g = graph(rnd, 4 + rnd.nextInt(10), 5 + rnd.nextInt(30), terms)
            val full = pairs(start, g, false)
            assertEquals(full, pairs(start, g, true), "graph $i")
            if (full.isNotEmpty()) nonEmpty++
        }
        assertTrue(nonEmpty >= 30, "non-trivial cases: $nonEmpty")
    }

    @Test
    fun compactReachabilityMatchesFullSppfOnRandomGraphs() {
        val rnd = Random(17)
        var nonEmpty = 0
        repeat(60) { i ->
            val (start, terms) = if (i % 2 == 0) dyck() to listOf(a, b, c, d) else ambiguous() to listOf(a, b)
            val g = graph(rnd, 4 + rnd.nextInt(10), 5 + rnd.nextInt(30), terms)
            val full = pairs(start, g, false)
            val compact = compactPairs(start, g)
            assertEquals(full, compact, "compact graph $i")
            if (full.isNotEmpty()) nonEmpty++
        }
        assertTrue(nonEmpty >= 30, "non-trivial cases: $nonEmpty")
    }

    @Test
    fun compactKeepsEpsilonAndTwoFinalStatesAtSameEndpoint() {
        val epsilon = Nonterminal("E")
        val epsilonStart = RsmState(epsilon, isStart = true, isFinal = true)
        epsilon.startState = epsilonStart
        val emptyGraph = InputGraph<Int, TerminalInputLabel>()
        emptyGraph.addStartVertex(0)
        assertEquals(setOf(0 to 0), compactPairs(fictive(epsilon), emptyGraph))

        val twoFinals = Nonterminal("T")
        val t0 = RsmState(twoFinals, isStart = true)
        val t1 = RsmState(twoFinals, isFinal = true)
        val t2 = RsmState(twoFinals, isFinal = true)
        twoFinals.startState = t0
        t0.addEdge(a, t1)
        t0.addEdge(b, t2)
        val graph = InputGraph<Int, TerminalInputLabel>()
        graph.addStartVertex(0)
        graph.addEdge(0, TerminalInputLabel(a), 1)
        graph.addEdge(0, TerminalInputLabel(b), 1)
        assertEquals(setOf(0 to 1), compactPairs(fictive(twoFinals), graph))
    }

    @Test
    fun compactReplaysPopsWhenSecondCallerArrivesLater() {
        val child = Nonterminal("child")
        val c0 = RsmState(child, isStart = true)
        val c1 = RsmState(child, isFinal = true)
        child.startState = c0
        c0.addEdge(c, c1)

        val parent = Nonterminal("parent")
        val p0 = RsmState(parent, isStart = true)
        val afterA = RsmState(parent)
        val afterB = RsmState(parent)
        val returnA = RsmState(parent)
        val returnB = RsmState(parent)
        val final = RsmState(parent, isFinal = true)
        parent.startState = p0
        p0.addEdge(a, afterA)
        p0.addEdge(b, afterB)
        afterA.addEdge(child, returnA)
        afterB.addEdge(child, returnB)
        returnA.addEdge(d, final)
        returnB.addEdge(a, final)

        val graph = InputGraph<Int, TerminalInputLabel>()
        for (v in 0..4) graph.addVertex(v)
        graph.addStartVertex(0)
        graph.addEdge(0, TerminalInputLabel(a), 1)
        graph.addEdge(0, TerminalInputLabel(b), 1)
        graph.addEdge(1, TerminalInputLabel(c), 2)
        graph.addEdge(2, TerminalInputLabel(d), 3)
        graph.addEdge(2, TerminalInputLabel(a), 4)
        val start = fictive(parent)
        val expected = setOf(0 to 3, 0 to 4)
        assertEquals(expected, pairs(start, graph, true))
        assertEquals(expected, compactPairs(start, graph))
    }

    @Test
    fun compactPropertyDoesNotBreakFullSppfFactory() {
        val nt = Nonterminal("Root")
        val start = RsmState(nt, isStart = true)
        val final = RsmState(nt, isFinal = true)
        nt.startState = start
        start.addEdge(a, final)
        val graph = InputGraph<Int, TerminalInputLabel>()
        graph.addStartVertex(0)
        graph.addEdge(0, TerminalInputLabel(a), 1)
        val previous = System.getProperty("ucfs.noSppfReachability")
        try {
            System.setProperty("ucfs.noSppfReachability", "true")
            val roots = Gll.gll(start, graph).parse()
            assertEquals(setOf(0 to 1), roots.map { it.inputRange!!.from to it.inputRange!!.to }.toSet())
            assertTrue(roots.single().children.any(), "full SPPF must retain its derivation")
        } finally {
            if (previous == null) System.clearProperty("ucfs.noSppfReachability")
            else System.setProperty("ucfs.noSppfReachability", previous)
        }
    }

    @Test
    fun compactRejectsStaleContextAccessAndReplacement() {
        val nt = Nonterminal("Root")
        val start = RsmState(nt, isStart = true)
        val final = RsmState(nt, isFinal = true)
        nt.startState = start
        start.addEdge(a, final)
        val graph = InputGraph<Int, TerminalInputLabel>()
        graph.addStartVertex(0)
        graph.addEdge(0, TerminalInputLabel(a), 1)
        val gll = Gll.gll(start, graph, reachabilityOnly = true, compactReachability = true)
        val initialContext = gll.ctx
        assertFailsWith<IllegalStateException> { gll.ctx = initialContext }
        assertEquals(setOf(0 to 1), gll.parse().map { it.inputFrom to it.inputTo }.toSet())
        val error = assertFailsWith<IllegalStateException> { gll.ctx.gss.nodes }
        assertTrue(error.message!!.contains("compact reachability"))
    }

    @Test
    fun compactResultSetRejectsDifferentNodeClassWithSameCoordinates() {
        val nt = Nonterminal("Root")
        val start = RsmState(nt, isStart = true)
        val final = RsmState(nt, isFinal = true)
        nt.startState = start
        start.addEdge(a, final)
        val graph = InputGraph<Int, TerminalInputLabel>()
        graph.addStartVertex(0)
        graph.addEdge(0, TerminalInputLabel(a), 1)
        val roots = Gll.gll(start, graph, true, true).parse()
        assertEquals(1, roots.size)
        assertFalse(roots.contains(LeafSppfNode(InputRange(0, 1), RsmRange(start, final), Range)))
    }
}
