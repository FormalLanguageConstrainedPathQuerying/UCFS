package grammars

import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.extension.StringExtension.times
import org.ucfs.grammar.combinator.regexp.*

class SimpleDyck : Grammar() {
    val S by Nt().asStart()
    init{
        S /= Option(S * "(" * S * ")")
    }
}