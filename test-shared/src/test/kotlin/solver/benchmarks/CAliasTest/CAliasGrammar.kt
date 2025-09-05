package solver.benchmarks.CAliasTest


import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.regexp.*
import org.ucfs.rsm.symbol.Term

class CAliasGrammar : Grammar() {
    val V_1 by Nt()
    val V_2 by Nt()
    val V_3 by Nt()
    val V by Nt(V_1*V_2*V_3)
    val S by Nt(Term("d_r") * V * Term("d")).asStart()
    init {
        V_2 /= S or Epsilon
        V_3 /= (Term("a") * V_2 * V_3) or Epsilon
        V_1 /= ( V_2  * Term("a_r") * V_1)  or Epsilon
    }
}
