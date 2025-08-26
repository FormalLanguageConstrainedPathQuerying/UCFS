package solver.correctnessTests.SimplifiedDyckGrammarTest


import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.extension.StringExtension.times
import org.ucfs.grammar.combinator.regexp.*

class SimplifiedDyckGrammar : Grammar() {
    val S by Nt().asStart()

    init {
        S /= Option("(" * S * ")")
        // S =  ( S ) ?
    }
}