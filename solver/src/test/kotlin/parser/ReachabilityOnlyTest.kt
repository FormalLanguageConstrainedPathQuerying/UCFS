package parser

import org.junit.jupiter.api.Test
import org.ucfs.input.InputGraph
import org.ucfs.input.TerminalInputLabel
import org.ucfs.parser.Gll
import org.ucfs.rsm.RsmState
import org.ucfs.rsm.symbol.Nonterminal
import org.ucfs.rsm.symbol.Term
import kotlin.random.Random
import kotlin.test.assertEquals
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
}
