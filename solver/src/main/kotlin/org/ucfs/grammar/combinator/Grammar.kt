package org.ucfs.grammar.combinator

import org.ucfs.grammar.combinator.regexp.Nt
import org.ucfs.rsm.RsmState


open class Grammar {
    val nonTerms = ArrayList<Nt>()

    private lateinit var startNt: Nt
    private lateinit var fictitiousStartNt: Nt

    val name: String = this.javaClass.simpleName.ifEmpty {
        //in case of lambda objects
        this.javaClass.name.substringBeforeLast('$').substringAfterLast('$')
    }

    private var _rsm: RsmState? = null
    val rsm: RsmState
        get() {
            if (_rsm == null) {
                _rsm = buildRsm()
            }
            return _rsm!!
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
        fictitiousStartNt = Nt(startNt, "fictiveStart")
        fictitiousStartNt.buildRsmBox()
        return fictitiousStartNt.nonterm.startState
    }
}
