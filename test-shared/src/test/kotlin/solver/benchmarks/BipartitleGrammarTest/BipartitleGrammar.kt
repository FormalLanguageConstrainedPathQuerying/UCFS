package solver.benchmarks.BipartitleGrammarTest


import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.regexp.*
import org.ucfs.rsm.symbol.Term

class BipartitleGrammar : Grammar() {
    val A by Nt()
    val B by Nt((Term("b") * A) or (Term("b")))
    val S by Nt(A).asStart()
    init {
        A /= (Term("a") * B) or (Term("a"))
    }
}
