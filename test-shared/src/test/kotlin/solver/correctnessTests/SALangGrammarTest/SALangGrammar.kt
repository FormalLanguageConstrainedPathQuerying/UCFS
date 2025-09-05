package solver.correctnessTests.SALangGrammarTest


import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.extension.StringExtension.times
import org.ucfs.grammar.combinator.regexp.*

class SALangGrammar : Grammar() {
    val A by Nt("a" * "b")
    val S by Nt((A or ("a" * "b")) * "c").asStart()
}