package descriptors

import org.junit.jupiter.api.Test
import org.ucfs.descriptors.Descriptor
import org.ucfs.descriptors.DescriptorsStorage
import org.ucfs.gss.GssNode
import org.ucfs.rsm.RsmState
import org.ucfs.rsm.symbol.Nonterminal
import org.ucfs.sppf.node.getEmptyRange
import kotlin.test.assertNull
import kotlin.test.assertSame

class DescriptorsStorageTest {
    @Test
    fun equivalentPendingDescriptorsAreHandledOnce() {
        val state = RsmState(Nonterminal("S"))
        val gss = GssNode(state, 1)
        val first = Descriptor(1, gss, state, getEmptyRange<Int>())
        val duplicate = Descriptor(1, gss, state, getEmptyRange<Int>())
        val other = Descriptor(2, gss, state, getEmptyRange<Int>())
        val storage = DescriptorsStorage<Int>()

        storage.add(first)
        storage.add(duplicate)
        storage.add(other)

        assertSame(other, storage.nextToHandle())
        assertSame(first, storage.nextToHandle())
        assertNull(storage.nextToHandle())
    }
}
