package solver.correctnessTests.EpsilonGrammarTest


import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.regexp.*

class EpsilonGrammar : Grammar() {

    val S by Nt(Epsilon).asStart()
}