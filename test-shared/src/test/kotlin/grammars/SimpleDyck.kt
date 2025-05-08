package grammars

import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.extension.StringExtension.times
import org.ucfs.grammar.combinator.regexp.*
import org.ucfs.grammar.combinator.regexp.Epsilon
import org.ucfs.rsm.symbol.Term

class SimplifiedDyck : Grammar() {
    val S by Nt().asStart()

    init {
        S /= Option("(" * S * ")")
    }
}

class LoopDyck : Grammar() {
    val S by Nt().asStart()

    init {
        S /= Option("(" * S * ")")
    }
}

class ABGrammar : Grammar() {
    val A by Nt(Term("a"))
    val C by Nt(Term("a"))
    val B by Nt(C)
    val S by Nt(A or B).asStart()
}

class SALang : Grammar() {
    val A by Nt("a" * "b")
    val S by Nt((A or ("a" * "b")) * "c").asStart()
}

class Epsilon : Grammar() {
    val S by Nt(Epsilon).asStart()
}