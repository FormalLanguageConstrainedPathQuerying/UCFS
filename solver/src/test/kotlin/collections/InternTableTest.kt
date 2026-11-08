package collections

import org.junit.jupiter.api.Test
import org.ucfs.collections.InternTable
import kotlin.random.Random
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InternTableTest {
    @Test
    fun guardedInternKeepsCanonicalKeysAfterRepeatedGrowth() {
        fun hash(id: Int) = when (id % 4) {
            0 -> 7                       // distinct keys with the same full hash
            1 -> id * 16 + 7             // distinct full hashes sharing low bits
            2 -> Int.MIN_VALUE + id      // negative hashes
            else -> id * 31
        }
        val table = InternTable<Key>(4, hashGuard = true)
        val stored = (0 until 512).map { Key(it, hash(it)) }
        stored.forEach { assertSame(it, table.intern(it)) }

        stored.shuffled(Random(44)).forEach { original ->
            val equivalent = Key(original.id, original.hash)
            assertSame(original, table.intern(equivalent))
            assertFalse(table.addIfAbsent(equivalent))
        }
    }

    @Test
    fun optionalHashGuardSkipsUnequalHashesButChecksRealCollisions() {
        val guarded = InternTable<CountedKey>(4, hashGuard = true)
        val first = CountedKey(1, 0)
        assertTrue(guarded.addIfAbsent(first))
        assertTrue(guarded.addIfAbsent(CountedKey(2, 4)))
        assertEquals(0, first.comparisons, "different hashes in the same slot skip equals")
        assertFalse(guarded.addIfAbsent(CountedKey(1, 0)))
        assertEquals(1, first.comparisons, "equal hashes still use equals")

        val ordinary = InternTable<CountedKey>(4)
        val ordinaryFirst = CountedKey(1, 0)
        assertTrue(ordinary.addIfAbsent(ordinaryFirst))
        assertTrue(ordinary.addIfAbsent(CountedKey(2, 4)))
        assertEquals(1, ordinaryFirst.comparisons, "default table keeps its existing lookup path")
    }

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

    private class CountedKey(val id: Int, val hash: Int) {
        var comparisons = 0
        override fun hashCode(): Int = hash
        override fun equals(other: Any?): Boolean {
            comparisons++
            return other is CountedKey && id == other.id
        }
    }
}
