package org.ucfs.rsm

import org.ucfs.grammar.combinator.regexp.Empty
import org.ucfs.grammar.combinator.regexp.Nt
import org.ucfs.grammar.combinator.regexp.Regexp
import org.ucfs.rsm.symbol.ITerminal
import org.ucfs.rsm.symbol.Nonterminal
import org.ucfs.rsm.symbol.Symbol
import java.util.*
import kotlin.collections.ArrayList

data class RsmEdge(val symbol: Symbol, val destinationState: RsmState)
//data class TerminalRsmEdge<Term: ITerminal>(val terminal: Term, val destinationState: RsmState)
//data class NonterminalRsmEdge(val nonterminal: Nonterminal, val destinationState: RsmState)

data class RsmState(
    val nonterminal: Nonterminal,
    val isStart: Boolean = false,
    val isFinal: Boolean = false,
    var numId: Int = nonterminal.getNextRsmStateId()
) {
    val id: String = "${nonterminal.name}_${(numId)}"

    val outgoingEdges
        get() = terminalEdgesStorage.plus(nonterminalEdgesStorage)

    val terminalEdgesStorage = ArrayList<RsmEdge>()

    val nonterminalEdgesStorage = ArrayList<RsmEdge>()


    /**
     * Adds edge from current rsmState to given destinationState via given symbol, terminal or nonterminal
     * @param symbol - symbol to store on edge
     * @param destinationState
     */
    fun addEdge(symbol: Symbol, destinationState: RsmState) {
        when (symbol) {
            is ITerminal -> terminalEdgesStorage.add(RsmEdge(symbol, destinationState))
            is Nonterminal -> nonterminalEdgesStorage.add(RsmEdge(symbol, destinationState))
            else -> throw RsmException("Unsupported type of symbol")
        }
    }

    protected fun getNewState(regex: Regexp): RsmState {
        return RsmState(this.nonterminal, isStart = false, regex.acceptEpsilon())
    }

    /**
     * Builds RSM from current state
     * @param rsmDescription - right hand side of the rule in GrammarDsl in the form of regular expression
     */
    fun buildRsmBox(rsmDescription: Regexp) {
        val regexpToProcess = Stack<Regexp>()
        val regexpToRsmState = HashMap<Regexp, RsmState>()
        regexpToRsmState[rsmDescription] = this

        val alphabet = rsmDescription.getAlphabet()

        regexpToProcess.add(rsmDescription)

        while (!regexpToProcess.empty()) {
            val regexp = regexpToProcess.pop()
            val state = regexpToRsmState[regexp]

            for (symbol in alphabet) {
                val newState = regexp.derive(symbol)
                if (newState !is Empty) {
                    if (!regexpToRsmState.containsKey(newState)) {
                        regexpToProcess.add(newState)
                    }
                    val destinationState = regexpToRsmState.getOrPut(newState) { getNewState(newState) }

                    when (symbol) {
                        is ITerminal -> {
                            state?.addEdge(symbol, destinationState)
                        }

                        is Nt -> {
                            if (!symbol.isInitialized()) {
                                throw IllegalArgumentException("Not initialized Nt used in description of \"${symbol.nonterm.name}\"")
                            }
                            state?.addEdge(symbol.nonterm, destinationState)
                        }
                    }
                }
            }
        }
    }
}