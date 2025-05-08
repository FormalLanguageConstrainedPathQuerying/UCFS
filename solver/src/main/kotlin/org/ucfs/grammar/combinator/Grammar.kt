package org.ucfs.grammar.combinator

import org.ucfs.grammar.combinator.regexp.Nt
import org.ucfs.grammar.combinator.regexp.Regexp
import org.ucfs.incrementalDfs
import org.ucfs.rsm.RsmState
import org.ucfs.rsm.symbol.ITerminal
import org.ucfs.rsm.symbol.Nonterminal


open class Grammar {
    val nonTerms = ArrayList<Nt>()

    private lateinit var startNt: Nt

    private var _rsm: RsmState? = null
    val rsm: RsmState
        get() {
            if (_rsm == null) {
                _rsm = buildRsm()
            }
            return _rsm!!
        }

    fun setStart(expr: Regexp) {
        if (expr is Nt) {
            startNt = expr
        } else throw IllegalArgumentException("Only NT object can be start state for Grammar")
    }

    fun Nt.asStart(): Nt {
        if (this@Grammar::startNt.isInitialized) {
            throw Exception("Nonterminal ${nonterm.name} is already initialized")
        }
        startNt = this
        return this
    }


    /**
     * Builds a Rsm for the grammar
     */
    private fun buildRsm(): RsmState {
        nonTerms.forEach { it.buildRsmBox() }
        //if nonterminal not initialized -- it will be checked in buildRsmBox()
        return startNt.nonterm.startState
    }
}
