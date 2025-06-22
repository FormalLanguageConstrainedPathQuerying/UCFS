package org.ucfs.sppf.node

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
    val id: Int = lastId++
    val children = ArrayList<RangeSppfNode<VertexType>>()
    override fun toString(): String {
        return when (type) {
            is TerminalType<*> -> "Terminal `${type.terminal}` $inputRange"
            is Range -> "Range $inputRange $rsmRange"
            is NonterminalType -> "Nonterminal ${type.startState.nonterminal.name} $inputRange $rsmRange"
            is IntermediateType<*> -> "Intermediate input:${type.inputPosition} rsm:${type.grammarSlot.id}"
            is EpsilonNonterminalType -> "Epsilon ${type.startState.id}"
            is EmptyType -> "Empty node"
            else -> "Unknown sppf node type!"
        }
    }
}

fun <VertexType> getEmptyRange(): RangeSppfNode<VertexType> {
    return RangeSppfNode(null, null, EmptyType())
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
class EmptyType : RangeType

var lastId = 0
