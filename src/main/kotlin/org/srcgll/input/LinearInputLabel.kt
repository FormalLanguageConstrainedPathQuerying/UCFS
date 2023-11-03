package org.srcgll.input

import org.srcgll.grammar.symbol.ITerminal

class LinearInputLabel
(
    override val terminal : ITerminal
)
    : ILabel
{
    override fun equals(other : Any?) : Boolean
    {
        if (this === other)                  return true
        if (other !is LinearInputLabel)      return false
        if (this.terminal != other.terminal) return false

        return true
    }

    val hashCode : Int = terminal.hashCode()
    override fun hashCode() = hashCode
}
