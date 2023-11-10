package org.srcgll.sppf.node

import org.srcgll.grammar.symbol.Nonterminal
import java.util.*

class SymbolSPPFNode <VertexType>
(
    val symbol  : Nonterminal<*>,
    leftExtent  : VertexType,
    rightExtent : VertexType,
)
    : ParentSPPFNode<VertexType>(leftExtent, rightExtent)
{
    override fun toString() =
        "SymbolSPPFNode(leftExtent=$leftExtent, rightExtent=$rightExtent, symbol=$symbol)"

    override fun equals(other : Any?) : Boolean
    {
        if (this === other)              return true
        if (other !is SymbolSPPFNode<*>) return false
        if (!super.equals(other))        return false
        if (symbol != other.symbol)      return false

        return true
    }

    override val hashCode : Int = Objects.hash(leftExtent, rightExtent, symbol)
    override fun hashCode() = hashCode
}
