package org.ucfs.sppf

import org.ucfs.rsm.RsmState
import org.ucfs.rsm.symbol.ITerminal
import org.ucfs.sppf.node.*

/**
 * @param InputEdgeType - type of vertex in input graph
 */
open class SppfStorage<InputEdgeType> {

    /**
     * Collection of created sppfNodes with access and search in O(1) time
     */
    private val createdSppfNodes: HashMap<RangeSppfNode<InputEdgeType>, RangeSppfNode<InputEdgeType>> = HashMap()

    protected fun addNode(node: RangeSppfNode<InputEdgeType>): RangeSppfNode<InputEdgeType> {
        return createdSppfNodes.getOrPut(node, { node })
    }

    protected fun makeNode(
        input: InputRange<InputEdgeType>?,
        rsm: RsmRange?,
        type: RangeType
    ): RangeSppfNode<InputEdgeType> = when (type) {
        is TerminalType<*>, is EpsilonNonterminalType, is EmptyType -> LeafSppfNode(input, rsm, type)
        is IntermediateType<*> -> BinarySppfNode(input, rsm, type)
        else -> VariadicSppfNode(input, rsm, type) // Range, NonterminalType
    }

    /**
     * Add nonterminal node after pop
     */
    open fun addNonterminalNode(
        input: InputRange<InputEdgeType>,
        rsm: RsmRange,
        startState: RsmState,
        childSppf: RangeSppfNode<InputEdgeType>? = null
    ): RangeSppfNode<InputEdgeType> {
        return if (childSppf == null) addNode(input, rsm, NonterminalType(startState))
        else addNode(input, rsm, NonterminalType(startState), listOf(childSppf))
    }

    /** Combine a popped nonterminal with the range before its call. */
    open fun addComposedNonterminalNode(
        left: RangeSppfNode<InputEdgeType>,
        rightInput: InputRange<InputEdgeType>,
        rightRsm: RsmRange,
        startState: RsmState,
        childSppf: RangeSppfNode<InputEdgeType>?
    ): RangeSppfNode<InputEdgeType> =
        addIntermediateNode(left, addNonterminalNode(rightInput, rightRsm, startState, childSppf))

    open fun addEpsilonNode(
        input: InputRange<InputEdgeType>,
        rsmRange: RsmRange,
        rsmState: RsmState
    ): RangeSppfNode<InputEdgeType> {
        return addNode(
            input, rsmRange, EpsilonNonterminalType(rsmState)
        )
    }

    /**
     * Add temrminal node
     */
    open fun addNode(
        input: InputRange<InputEdgeType>, rsm: RsmRange, terminal: ITerminal
    ): RangeSppfNode<InputEdgeType> {
        return addNode(input, rsm, TerminalType(terminal))
    }

    open fun addIntermediateNode(
        leftSubtree: RangeSppfNode<InputEdgeType>,
        rightSubtree: RangeSppfNode<InputEdgeType>
    ): RangeSppfNode<InputEdgeType> {
        if (leftSubtree.type is EmptyType) {
            return rightSubtree
        }
        return addNode(
            InputRange(
                leftSubtree.inputRange!!.from, rightSubtree.inputRange!!.to
            ), RsmRange(
                leftSubtree.rsmRange!!.from, rightSubtree.rsmRange!!.to
            ), IntermediateType(
                leftSubtree.rsmRange!!.to, leftSubtree.inputRange!!.to
            ), listOf(leftSubtree, rightSubtree)
        )
    }

    protected open fun addNode(
        input: InputRange<InputEdgeType>,
        rsm: RsmRange,
        rangeType: RangeType,
        children: List<RangeSppfNode<InputEdgeType>> = listOf()
    ): RangeSppfNode<InputEdgeType> {
        val rangeNode = addNode(makeNode(input, rsm, Range))
        val valueRsm = if (rangeType is TerminalType<*>) null else rsm
        val valueNode = addNode(makeNode(input, valueRsm, rangeType))

        if (!rangeNode.hasChild(valueNode)) {
            rangeNode.addChild(valueNode)
        }
        for (child in children) {
            if (!valueNode.hasChild(child)) {
                valueNode.addChild(child)
            }
        }
        return rangeNode
    }
}

/**
 * Reachability-only storage: keeps just the deduplicated range nodes (input range x RSM range), without value
 * nodes and children. The parser's descriptors refer to range nodes only, so the set of descriptors and the parse
 * results are the same as with [SppfStorage]; derivation trees (and paths) are not available.
 */
class ReachabilitySppfStorage<InputEdgeType> : SppfStorage<InputEdgeType>() {
    private fun rangeNode(input: InputRange<InputEdgeType>, rsm: RsmRange): RangeSppfNode<InputEdgeType> =
        addNode(ReachabilityRangeSppfNode(input, rsm))

    override fun addNonterminalNode(
        input: InputRange<InputEdgeType>,
        rsm: RsmRange,
        startState: RsmState,
        childSppf: RangeSppfNode<InputEdgeType>?
    ): RangeSppfNode<InputEdgeType> = rangeNode(input, rsm)

    override fun addComposedNonterminalNode(
        left: RangeSppfNode<InputEdgeType>,
        rightInput: InputRange<InputEdgeType>,
        rightRsm: RsmRange,
        startState: RsmState,
        childSppf: RangeSppfNode<InputEdgeType>?
    ): RangeSppfNode<InputEdgeType> {
        if (left.type is EmptyType) return rangeNode(rightInput, rightRsm)
        return rangeNode(
            InputRange(left.inputRange!!.from, rightInput.to),
            RsmRange(left.rsmRange!!.from, rightRsm.to)
        )
    }

    override fun addEpsilonNode(
        input: InputRange<InputEdgeType>,
        rsmRange: RsmRange,
        rsmState: RsmState
    ): RangeSppfNode<InputEdgeType> = rangeNode(input, rsmRange)

    override fun addNode(
        input: InputRange<InputEdgeType>,
        rsm: RsmRange,
        terminal: ITerminal
    ): RangeSppfNode<InputEdgeType> = rangeNode(input, rsm)

    override fun addIntermediateNode(
        leftSubtree: RangeSppfNode<InputEdgeType>,
        rightSubtree: RangeSppfNode<InputEdgeType>
    ): RangeSppfNode<InputEdgeType> {
        if (leftSubtree.type is EmptyType) return rightSubtree
        return rangeNode(
            InputRange(leftSubtree.inputRange!!.from, rightSubtree.inputRange!!.to),
            RsmRange(leftSubtree.rsmRange!!.from, rightSubtree.rsmRange!!.to)
        )
    }

    override fun addNode(
        input: InputRange<InputEdgeType>,
        rsm: RsmRange,
        rangeType: RangeType,
        children: List<RangeSppfNode<InputEdgeType>>
    ): RangeSppfNode<InputEdgeType> = rangeNode(input, rsm)
}
