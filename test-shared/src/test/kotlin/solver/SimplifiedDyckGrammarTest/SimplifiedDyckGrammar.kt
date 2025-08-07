package solver.SimplifiedDyckGrammarTest


import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.extension.StringExtension.or
import org.ucfs.grammar.combinator.extension.StringExtension.times
import org.ucfs.grammar.combinator.regexp.*
import org.ucfs.grammar.combinator.regexp.Epsilon
import org.ucfs.rsm.symbol.Term

class SimplifiedDyckGrammar : Grammar() {
    val S by Nt().asStart()

    init {
        S /= Option("(" * S * ")")
        // S =  ( S ) ?
    }
}