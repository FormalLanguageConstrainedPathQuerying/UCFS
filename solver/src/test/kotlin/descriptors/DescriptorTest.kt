package descriptors

import org.junit.jupiter.api.Test
import org.ucfs.descriptors.Descriptor
import org.ucfs.gss.GssNode
import org.ucfs.rsm.RsmState
import org.ucfs.rsm.symbol.Nonterminal
import org.ucfs.sppf.ReachabilitySppfStorage
import org.ucfs.sppf.SppfStorage
import org.ucfs.sppf.node.InputRange
import org.ucfs.sppf.node.RsmRange
import org.ucfs.sppf.node.getEmptyRange
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class DescriptorTest {
    @Test
    fun canonicalNodesAndFreshEmptyNodesHaveConsistentDescriptorKeys() {
        val state = RsmState(Nonterminal("S"))
        val gss = GssNode(state, 1)
        for (storage in listOf(SppfStorage<Int>(), ReachabilitySppfStorage<Int>())) {
            val first = storage.addEpsilonNode(InputRange(1, 1), RsmRange(state, state), state)
            val second = storage.addEpsilonNode(InputRange(1, 1), RsmRange(state, state), state)
            assertSame(first, second)
            val firstDescriptor = Descriptor(1, gss, state, first)
            val secondDescriptor = Descriptor(1, gss, state, second)
            assertEquals(firstDescriptor, secondDescriptor)
            assertEquals(firstDescriptor.hashCode(), secondDescriptor.hashCode())
        }

        val firstEmpty = getEmptyRange<Int>()
        val secondEmpty = getEmptyRange<Int>()
        assertNotSame(firstEmpty, secondEmpty)
        val firstDescriptor = Descriptor(1, gss, state, firstEmpty)
        val secondDescriptor = Descriptor(1, gss, state, secondEmpty)
        assertEquals(firstDescriptor, secondDescriptor)
        assertEquals(firstDescriptor.hashCode(), secondDescriptor.hashCode())
    }
}
