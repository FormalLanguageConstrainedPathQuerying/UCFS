package parser

import org.junit.jupiter.api.Test
import org.ucfs.parser.CompactDescriptor
import org.ucfs.parser.CompactGssNode
import org.ucfs.rsm.RsmState
import org.ucfs.rsm.symbol.Nonterminal
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CompactDescriptorTest {
    @Test
    fun emptyPrefixBitSeparatesOtherwiseEqualDescriptorKeys() {
        val state = RsmState(Nonterminal("S"), isStart = true, isFinal = true)
        val gss = CompactGssNode(state, 3)
        val empty = CompactDescriptor(3, gss, state, emptyPrefix = true)
        val epsilon = CompactDescriptor(3, gss, state, emptyPrefix = false)

        assertNotEquals(empty, epsilon)
        assertEquals(empty, CompactDescriptor(3, gss, state, emptyPrefix = true))
        assertEquals(epsilon, CompactDescriptor(3, gss, state, emptyPrefix = false))
    }
}
