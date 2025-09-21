package solver.correctnessTests.AmbiguousAStar2GrammarTest


import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.extension.StringExtension.or
import org.ucfs.grammar.combinator.regexp.*

class AmbiguousAStar2Grammar : Grammar() {
    val S by Nt().asStart()

    init {
        S /= "a" or S * S
    }
}