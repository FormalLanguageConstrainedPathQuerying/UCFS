package org.ucfs.sppf.node

import org.ucfs.input.ILabel
import org.ucfs.parser.context.Context
import org.ucfs.rsm.RsmState
import org.ucfs.rsm.symbol.ITerminal

/**
 * Base class for SPPF range nodes.
 * Represents all possible ways to derive a specific range.
 * Contains two ranges: one in the RSM and one in the input graph.
 * Nodes are deduplicated via [SppfStorage] and can be reused.
 *
 * Specialized into three subclasses based on the number of children:
 * - [LeafSppfNode] — no children (terminal, epsilon, empty nodes)
 * - [BinarySppfNode] — exactly two children (intermediate nodes)
 * - [VariadicSppfNode] — variable number of children (range, nonterminal nodes)
 *
 * @param VertexType - type of vertex in input graph
 */
sealed class RangeSppfNode<VertexType> {
    abstract val inputRange: InputRange<VertexType>?
    abstract val rsmRange: RsmRange?
    abstract val type: RangeType
    abstract fun hasChild(target: RangeSppfNode<VertexType>): Boolean
    abstract fun addChild(child: RangeSppfNode<VertexType>)
    abstract val children: Iterable<RangeSppfNode<VertexType>>
}

data class LeafSppfNode<VertexType>(
    override val inputRange: InputRange<VertexType>?,
    override val rsmRange: RsmRange?,
    override val type: RangeType,
) : RangeSppfNode<VertexType>() {
    override val children = emptyList<RangeSppfNode<VertexType>>()

    override fun hasChild(target: RangeSppfNode<VertexType>) = false

    override fun addChild(child: RangeSppfNode<VertexType>) = throw UnsupportedOperationException()
}

data class BinarySppfNode<VertexType>(
    override val inputRange: InputRange<VertexType>?,
    override val rsmRange: RsmRange?,
    override val type: RangeType,
) : RangeSppfNode<VertexType>() {
    var child0: RangeSppfNode<VertexType>? = null
    var child1: RangeSppfNode<VertexType>? = null

    override val children: Iterable<RangeSppfNode<VertexType>> get() = listOfNotNull(child0, child1)

    override fun hasChild(target: RangeSppfNode<VertexType>) =
        child0 === target || child1 === target

    override fun addChild(child: RangeSppfNode<VertexType>) {
        when {
            child0 == null -> child0 = child
            child1 == null -> child1 = child
            else -> throw IllegalStateException("BinarySppfNode already has 2 children")
        }
    }
}

data class VariadicSppfNode<VertexType>(
    override val inputRange: InputRange<VertexType>?,
    override val rsmRange: RsmRange?,
    override val type: RangeType,
) : RangeSppfNode<VertexType>() {
    private var _child0: RangeSppfNode<VertexType>? = null
    private var _child1: RangeSppfNode<VertexType>? = null
    private var _rest: ArrayList<RangeSppfNode<VertexType>>? = null
    private var _size = 0

    override val children: Iterable<RangeSppfNode<VertexType>> get() = object : Iterable<RangeSppfNode<VertexType>> {
        override fun iterator() = object : Iterator<RangeSppfNode<VertexType>> {
            var i = 0

            override fun hasNext() = i < _size

            override fun next(): RangeSppfNode<VertexType> = when (i++) {
                0 -> _child0!!
                1 -> _child1!!
                else -> _rest!![i - 3]
            }
        }
    }

    override fun hasChild(target: RangeSppfNode<VertexType>): Boolean {
        if (_child0 === target) return true
        if (_child1 === target) return true
        _rest?.forEach { if (it === target) return true }
        return false
    }

    override fun addChild(child: RangeSppfNode<VertexType>) {
        when (_size) {
            0 -> _child0 = child
            1 -> _child1 = child
            else -> {
                if (_rest == null) _rest = ArrayList(2)
                _rest!!.add(child)
            }
        }
        _size++
    }
}

fun <VertexType> getEmptyRange( isStart: Boolean = false): RangeSppfNode<VertexType>  {
    val type = EmptyType()
    if(isStart) {
        type.isStart = isStart
    }
    return LeafSppfNode(null, null, type)
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
