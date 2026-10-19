package rsm

import org.junit.jupiter.api.Test
import org.ucfs.rsm.RsmState
import org.ucfs.rsm.symbol.Nonterminal
import org.ucfs.rsm.symbol.Term
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class TerminalIndexTest {
    @Test
    fun indexReturnsFirstMatchingEdgeLikeLinearFind() {
        val nt = Nonterminal("S")
        val s = RsmState(nt, isStart = true)
        val a = RsmState(nt)
        val b = RsmState(nt, isFinal = true)
        s.addEdge(Term("x"), a)
        s.addEdge(Term("y"), b)
        s.addEdge(Term("x"), b) // duplicate symbol: the first edge wins, as with find
        assertSame(a, s.getTerminalEdge(Term("x"))!!.destinationState)
        assertSame(b, s.getTerminalEdge(Term("y"))!!.destinationState)
        assertNull(s.getTerminalEdge(Term("z")))
        s.addEdge(Term("z"), a) // edges added after a lookup must be visible
        assertSame(a, s.getTerminalEdge(Term("z"))!!.destinationState)
    }

    @Test
    fun indexAgreesWithFindOnRandomStates() {
        val rnd = Random(1)
        val nt = Nonterminal("R")
        repeat(50) {
            val s = RsmState(nt)
            val targets = List(5) { RsmState(nt) }
            repeat(rnd.nextInt(200)) { s.addEdge(Term("t${rnd.nextInt(60)}"), targets[rnd.nextInt(5)]) }
            for (i in 0 until 70) {
                val t = Term("t$i")
                assertEquals(s.terminalEdgesStorage.find { it.symbol == t }, s.getTerminalEdge(t))
            }
        }
    }
}
