package solver.correctnessTests.ABGrammarTest


import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.regexp.*
import org.ucfs.rsm.symbol.Term

class ABGrammar : Grammar() {
    val A by Nt(Term("a")) // A -> a
    val C by Nt(Term("a"))
    val B by Nt(C)  // C -> B
    val S by Nt(A or B).asStart()
}
