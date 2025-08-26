package solver.benchmarks.LoopDyckGrammarTest


import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.extension.StringExtension.times
import org.ucfs.grammar.combinator.regexp.*

class LoopDyckGrammar : Grammar() {
    val S by Nt().asStart()

    init {
        S /= Many("(" * S * ")")
        // S = [ ( S ) ]*
    }
}
