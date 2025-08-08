package solver.corectnessTests.StrangeDyckGrammarTest


import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.extension.StringExtension.or
import org.ucfs.grammar.combinator.extension.StringExtension.times
import org.ucfs.grammar.combinator.regexp.*

class StrangeDyckGrammar : Grammar() {
    val S by Nt().asStart()

    init {
        S /= "(" * S * ")" or "a"
        // S = eps | ( S )
    }
}
