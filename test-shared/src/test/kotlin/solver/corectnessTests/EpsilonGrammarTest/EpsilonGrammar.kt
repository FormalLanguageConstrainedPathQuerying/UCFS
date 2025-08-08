package solver.corectnessTests.EpsilonGrammarTest


import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.extension.StringExtension.times
import org.ucfs.grammar.combinator.regexp.*

class EpsilonGrammar : Grammar() {

    val S by Nt(Epsilon).asStart()
}