package org.ucfs.paths

import org.junit.jupiter.api.Test
import org.ucfs.parser.Gll
import org.ucfs.rsm.symbol.Term
import org.ucfs.sppf.node.*
import kotlin.test.*

class PathExtractionTest {
    private fun paths(graph: String, maxDepth: Int = 30): Set<String> =
        Gll.gll(PointsToGrammar().rsm, readGraph(graph)).parse()
            .flatMap { getPathFromSppf(it, maxDepth) ?: emptyList() }.map { it.toString() }.toSet()
    // Hand-built SPPF: `slot` is the start state of S, i.e. a nonterminal that is not collapsed like Alias/PointsTo.
    private val slot = PointsToGrammar().rsm
    private fun leaf(from: Int, s: String, to: Int) = RangeSppfNode(InputRange(from, to), null, TerminalType(Term(s)))
    private fun node(type: RangeType, vararg children: RangeSppfNode<Int>) =
        RangeSppfNode<Int>(null, null, type).apply { this.children.addAll(children) }
    private val ab = node(IntermediateType(slot, 1), leaf(0, "a", 1), leaf(1, "b", 2))

    @Test
    fun `leaf node types`() {
        assertEquals("[[(0-a->1)]]", getPathFromSppf(leaf(0, "a", 1), 1).toString(), "terminal is one edge")
        assertNull(getPathFromSppf(leaf(0, "a", 1), 0), "maxDepth 0 cuts the traversal")
        assertEquals(listOf(emptyList()), getPathFromSppf(node(EpsilonNonterminalType(slot)), 1), "epsilon")
        assertFailsWith<RuntimeException>("EmptyType is invalid in SPPF") { getPathFromSppf(node(EmptyType()), 1) }
    }

    @Test
    fun `intermediate and nonterminal concatenate children, range unions alternatives`() {
        val expected = listOf("[(0-a->1), (1-b->2)]", "[(0-c->2)]")
        val range = node(Range, node(NonterminalType(slot), ab), leaf(0, "c", 2))
        assertEquals(expected, getPathFromSppf(range, 4)?.map { it.toString() }, "both alternatives")
    }

    @Test
    fun `exhausted depth gives null for sequences and drops range alternatives`() {
        assertNull(getPathFromSppf(ab, 1), "intermediate with a null child")
        assertNull(getPathFromSppf(node(NonterminalType(slot), ab), 2), "nonterminal with a null child")
        assertNull(getPathFromSppf(node(Range, ab), 2), "range whose only alternative is null")
        val range = node(Range, ab, leaf(0, "c", 2))
        assertEquals("[[(0-c->2)]]", getPathFromSppf(range, 2).toString(), "only the alternative that fits")
    }

    @Test
    fun `example 1 - all paths from README are extracted`() {
        val expected = setOf(
            "[(1-PointsTo->0)]", "[(1-Alias->2), (2-store_0->3), (3-PointsTo->4)]",
            "[(1-Alias->2), (2-store_0->3), (3-Alias->5), (5-store_1->6), (6-PointsTo->7)]",
        )
        assertEquals(expected, paths("graph_1.dot"), "graph_1")
    }

    // Alias(1,8) needs the field bridge load_f Alias store_f (Dietrich et al. OOPSLA'15, Eq. 1):
    // 8 -assign-> 6 -load_0-> 4 -Alias-> 4 -store_0-> 1 -alloc-> 0. The duplicate (8-store_2->10) vs
    // (8-Alias->8), (8-store_2->10) comes from S (project extension) allowing both ε and reflexive Alias(8,8).
    @Test
    fun `example 4 - paths from two start vertices`() {
        val expected = setOf(
            "[(1-Alias->9), (9-store_3->11), (11-PointsTo->13)]", "[(1-Alias->8), (8-store_2->10), (10-PointsTo->12)]",
            "[(8-Alias->9), (9-store_3->11), (11-PointsTo->13)]", "[(8-store_2->10), (10-PointsTo->12)]",
            "[(8-Alias->8), (8-store_2->10), (10-PointsTo->12)]", "[(1-PointsTo->0)]", "[(8-PointsTo->0)]",
        )
        assertEquals(expected, paths("graph_4.dot"), "graph_4")
    }

    // Path n.(f.)^k = new X() of examples 2 and 3: Alias, then k times store_0 joined by Alias, then PointsTo.
    private fun loopPath(v: Int, f: Int, t: Int, a: Int, k: Int) =
        "[($v-Alias->$f), ${"($f-store_0->$t), ($t-Alias->$f), ".repeat(k - 1)}($f-store_0->$t), ($t-PointsTo->$a)]"

    @Test
    fun `examples 2 and 3 - cyclic SPPF gives finite path set growing with maxDepth`() {
        for ((graph, args) in listOf("graph_2.dot" to listOf(0, 2, 3, 4), "graph_3.dot" to listOf(0, 1, 2, 3))) {
            val (v, f, t, a) = args
            val shallow = paths(graph)
            assertTrue((1..6).all { loopPath(v, f, t, a, it) in shallow }, "$graph: k=1..6 in $shallow")
            val deep = paths(graph, maxDepth = 40)
            assertTrue(deep.containsAll(shallow) && deep.size > shallow.size, "$graph: 40 ⊋ 30, got $deep")
        }
    }
}
