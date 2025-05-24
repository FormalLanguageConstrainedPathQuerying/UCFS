package org.ucfs.input

import org.ucfs.rsm.symbol.ITerminal
import org.ucfs.rsm.symbol.Nonterminal

@JvmInline
value class LightSymbol(val index: Int)

val Eps = LightSymbol(0)

object SymbolRegistry {
    private var terminalIndex = 1
    private var nonterminalIndex = 1
    private val terminals = HashMap<ITerminal, LightSymbol>()
    private val nonterminals = HashMap<Nonterminal, LightSymbol>()
    private val sourceTerminals = arrayListOf<ITerminal>()
    private val sourceNonterminals = arrayListOf<Nonterminal>()

    fun registerTerminal(iTerminal: ITerminal): LightSymbol =
        terminals[iTerminal] ?: LightSymbol(terminalIndex++)
            .also { terminals[iTerminal] = it }
            .also { sourceTerminals.add(iTerminal) }

    fun registerNonterminal(nonterminal: Nonterminal): LightSymbol =
        nonterminals[nonterminal] ?: LightSymbol(-nonterminalIndex++)
            .also { nonterminals[nonterminal] = it }
            .also { sourceNonterminals.add(nonterminal) }

    fun getITerminal(terminal: LightSymbol): ITerminal = sourceTerminals[terminal.index - 1]

    fun getNonterminal(nonterminal: LightSymbol): Nonterminal = sourceNonterminals[-1 - nonterminal.index]
}

val LightSymbol.terminal
    get() = SymbolRegistry.getITerminal(this)

val LightSymbol.nonTerminal
    get() = SymbolRegistry.getNonterminal(this)

val LightSymbol.symbol
    get() = if (isNonterminal()) nonTerminal else terminal

fun LightSymbol.isNonterminal() = index < 0
