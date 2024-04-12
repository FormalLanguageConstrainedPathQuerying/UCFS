package org.srcgll.rsm.symbol

import org.srcgll.grammar.combinator.regexp.DerivedSymbol

interface ITerminal : Symbol, DerivedSymbol{
    /**
     * In generated parser `getTerminals` should return terminal in deterministic order
     * Can't use Comparable interface here because we can't implement if for Enums
     */
    fun getComparator(): Comparator<ITerminal>
}
