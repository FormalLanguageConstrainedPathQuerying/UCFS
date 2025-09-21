package grammars_example

import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.extension.StringExtension.or
import org.ucfs.grammar.combinator.extension.StringExtension.times
import org.ucfs.grammar.combinator.regexp.*
import org.ucfs.grammar.combinator.regexp.Epsilon
import org.ucfs.rsm.symbol.Term

class SimplifiedDyck : Grammar() {
    val S by Nt().asStart()

    init {
        S /= Option("(" * S * ")")
        // S =  ( S ) ?
    }
}

class StrangeDyck : Grammar() {
    val S by Nt().asStart()

    init {
        S /= "(" * S * ")" or "a"
        // S = eps | ( S )
    }
}

class LoopDyck : Grammar() {
    val S by Nt().asStart()

    init {
        S /= Many("(" * S * ")")
        // S = [ ( S ) ]*
    }
}

class ABGrammar : Grammar() {
    val A by Nt(Term("a")) // A -> a
    val C by Nt(Term("a"))
    val B by Nt(C)  // C -> B
    val S by Nt(A or B).asStart()
}

class SALang : Grammar() {
    val A by Nt("a" * "b")
    val S by Nt((A or ("a" * "b")) * "c").asStart()
}

class Epsilon : Grammar() {
    val S by Nt(Epsilon).asStart()
}

/**
 * Can parse only one symbol 'a'
 */
class AmbiguousAStar1 : Grammar() {
    val S by Nt().asStart()

    init {
        S /= "a" or S
    }
}

class AmbiguousAStar2 : Grammar() {
    val S by Nt().asStart()

    init {
        S /= "a" or S * S
    }
}


class AmbiguousAStar3 : Grammar() {
    val S by Nt().asStart()

    init {
        S /= "a" or S * S
    }
}