package solver.corectnessTests.AmbiguousAStar1GrammarTest


import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.extension.StringExtension.or
import org.ucfs.grammar.combinator.regexp.*

class AmbiguousAStar1Grammar : Grammar() {
    val S by Nt().asStart()

    init {
        S /= "a" or S
    }
}