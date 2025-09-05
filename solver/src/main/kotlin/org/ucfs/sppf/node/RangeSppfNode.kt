package org.ucfs.sppf.node

import org.ucfs.input.ILabel
import org.ucfs.parser.context.Context
import org.ucfs.rsm.RsmState
import org.ucfs.rsm.symbol.ITerminal

/**
 *
 * A Range node which corresponds to a matched range. It
 * represents all possible ways to get a specific range and can
 * have arbitrary many children. A child node can be of any
 * type, besides a Range node. Nodes of this type can be reused.
 * <p>
 *     Contains two range: in RSM and in Input graph
 *<p>
 * May be used as extended packed sppfNode.
 * @param VertexType - type of vertex in input graph
 */

data class RangeSppfNode<VertexType>(
    val inputRange: InputRange<VertexType>?,
    val rsmRange: RsmRange?,
    val type: RangeType,
) {
    val children = ArrayList<RangeSppfNode<VertexType>>()
}

fun <VertexType> getEmptyRange( isStart: Boolean = false): RangeSppfNode<VertexType>  {
    val type = EmptyType()
    if(isStart) {
        type.isStart = isStart
    }
    return RangeSppfNode(null, null, type)
}

data class InputRange<VertexType>(
    val from: VertexType,
    val to: VertexType,
) {
    override fun toString(): String = "input:[$from;$to]"
}

data class RsmRange(
    val from: RsmState,
    val to: RsmState,
) {
    override fun toString(): String = "rsm:[${from.id};${to.id}]"
}

interface RangeType

object Range : RangeType
data class TerminalType<T : ITerminal>(val terminal: T) : RangeType
data class NonterminalType(val startState: RsmState) : RangeType
data class EpsilonNonterminalType(val startState: RsmState) : RangeType
data class IntermediateType<VertexType>(val grammarSlot: RsmState, val inputPosition: VertexType) : RangeType
class EmptyType : RangeType {
    var isStart = false

    @Override
    override fun equals(other: Any?): Boolean {
        return other is EmptyType
    }

    @Override
    override fun hashCode(): Int {
        return 12
    }
}
