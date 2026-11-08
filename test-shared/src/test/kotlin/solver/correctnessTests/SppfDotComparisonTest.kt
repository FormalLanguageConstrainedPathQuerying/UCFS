package solver.correctnessTests

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SppfDotComparisonTest {
    @Test
    fun ignoresTraversalNumbersButChecksEdges() {
        val first = """
            digraph g {
            subgraph cluster_0{
            _0_0 [label = "0     Range input: [0, 1]", shape = ellipse]
            _0_1 [label = "1     Terminal a", shape = rectangle]
            _0_0->_0_1
            }
            }
        """.trimIndent()
        val renumbered = """
            digraph g {
            subgraph cluster_1{
            _1_0 [label = "0     Terminal a", shape = rectangle]
            _1_1 [label = "1     Range input: [0, 1]", shape = ellipse]
            _1_1->_1_0
            }
            }
        """.trimIndent()
        val missingEdge = renumbered.replace("_1_1->_1_0", "")

        assertTrue(sameSppfDot(first, renumbered))
        assertFalse(sameSppfDot(first, missingEdge))
    }

    @Test
    fun distinguishesEqualLabelNodesWithDifferentChildren() {
        val forest = """
            digraph g {
            subgraph cluster_0{
            _0_0 [label = "0     Root left", shape = ellipse]
            _0_1 [label = "1     Root right", shape = ellipse]
            _0_2 [label = "2     Nonterminal A", shape = invtrapezium]
            _0_3 [label = "3     Nonterminal A", shape = invtrapezium]
            _0_4 [label = "4     Terminal x", shape = rectangle]
            _0_5 [label = "5     Terminal y", shape = rectangle]
            _0_0->_0_2
            _0_1->_0_3
            _0_2->_0_4
            _0_3->_0_5
            }
            }
        """.trimIndent()
        val swappedChildren = forest.replace("_0_2->_0_4", "_0_2->_0_5")
            .replace("_0_3->_0_5", "_0_3->_0_4")

        assertFalse(sameSppfDot(forest, swappedChildren))
    }
}
