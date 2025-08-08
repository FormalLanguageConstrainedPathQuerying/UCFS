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

    private fun addNode(node: RangeSppfNode<InputEdgeType>): RangeSppfNode<InputEdgeType> {
        return createdSppfNodes.getOrPut(node, { node })
    }

    /**
     * Add nonterminal node after pop
     */
    fun addNonterminalNode(
        input: InputRange<InputEdgeType>,
        rsm: RsmRange,
        startState: RsmState,
        childSppf: RangeSppfNode<InputEdgeType>? = null
    ): RangeSppfNode<InputEdgeType> {
        return if (childSppf == null) addNode(input, rsm, NonterminalType(startState))
        else addNode(input, rsm, NonterminalType(startState), listOf(childSppf))
    }

    fun addEpsilonNode(
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
    fun addNode(
        input: InputRange<InputEdgeType>, rsm: RsmRange, terminal: ITerminal
    ): RangeSppfNode<InputEdgeType> {
        return addNode(input, rsm, TerminalType(terminal))
    }

    fun addIntermediateNode(
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
                leftSubtree.rsmRange.to, leftSubtree.inputRange.to
            ), listOf(leftSubtree, rightSubtree)
        )
    }

    private fun addNode(
        input: InputRange<InputEdgeType>,
        rsm: RsmRange,
        rangeType: RangeType,
        children: List<RangeSppfNode<InputEdgeType>> = listOf()
    ): RangeSppfNode<InputEdgeType> {
        val rangeNode = addNode(RangeSppfNode(input, rsm, Range))
        val valueRsm = if (rangeType is TerminalType<*>) null else rsm
        val valueNode = addNode(RangeSppfNode(input, valueRsm, rangeType))
        if (!rangeNode.children.contains(valueNode)) {
            rangeNode.children.add(valueNode)
        }
        for (child in children) {
            if (!valueNode.children.contains(child)) {
                valueNode.children.add(child)
            }
        }
        return rangeNode
    }
}