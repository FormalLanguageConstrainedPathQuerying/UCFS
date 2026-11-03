package collections

import org.junit.jupiter.api.Test
import org.ucfs.collections.InternTable
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InternTableTest {
    @Test
    fun equalKeysAfterCollisionsReturnFirstStoredInstance() {
        val table = InternTable<Key>(4)
        val first = Key(1, 7)
        val second = Key(2, 7)
        table.intern(first)
        table.intern(second)

        assertSame(first, table.intern(Key(1, 7)))
        assertSame(second, table.intern(Key(2, 7)))
    }

    @Test
    fun negativeAndMinimumHashesStayAddressable() {
        val table = InternTable<Key>(4)
        val negative = Key(1, -7)
        val minimum = Key(2, Int.MIN_VALUE)
        table.intern(negative)
        table.intern(minimum)

        assertSame(negative, table.intern(Key(1, -7)))
        assertSame(minimum, table.intern(Key(2, Int.MIN_VALUE)))
    }

    @Test
    fun repeatedGrowthPreservesEveryCanonicalReference() {
        val table = InternTable<Key>(4)
        val stored = (0 until 1000).map { Key(it, it * 31) }
        stored.forEach(table::intern)

        stored.forEach { first -> assertSame(first, table.intern(Key(first.id, first.hash))) }
    }

    @Test
    fun longCollisionChainSurvivesGrowthAndBooleanInsertDetectsDuplicates() {
        val table = InternTable<Key>(4)
        val first = (0 until 64).map { Key(it, 7) }
        first.forEach { assertTrue(table.addIfAbsent(it)) }

        first.forEach { original ->
            assertSame(original, table.intern(Key(original.id, 7)))
            assertFalse(table.addIfAbsent(Key(original.id, 7)))
            assertFalse(table.addIfAbsent(original))
        }
    }

    private class Key(val id: Int, val hash: Int) {
        override fun hashCode(): Int = hash
        override fun equals(other: Any?): Boolean = other is Key && id == other.id
    }
}
