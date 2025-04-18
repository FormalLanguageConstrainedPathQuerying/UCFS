package org.ucfs.sppf

import org.ucfs.sppf.node.*

/**
 * @param InputEdgeType - type of vertex in input graph
 */
open class SppfStorage<InputEdgeType> {
    /**
     * Collection of created sppfNodes with access and search in O(1) time
     */
    private val createdSppfNodes: HashMap<RangeSppfNode<InputEdgeType>, RangeSppfNode<InputEdgeType>> = HashMap()


    fun addNode(node: RangeSppfNode<InputEdgeType>): RangeSppfNode<InputEdgeType> {
        return createdSppfNodes.getOrPut(node, { node })
    }

    fun addNode(leftSubtree: RangeSppfNode<InputEdgeType>, rightSubtree: RangeSppfNode<InputEdgeType>)
    : RangeSppfNode<InputEdgeType> {
        if(leftSubtree.type == EmptyType) {
            return rightSubtree
        }
        val newIntermediateNode = RangeSppfNode(
            InputRange(
                leftSubtree.inputRange!!.from,
                rightSubtree.inputRange!!.to),
            RsmRange(
                leftSubtree.rsmRange!!.rsmFrom,
                rightSubtree.rsmRange!!.rsmTo),
            IntermediateType(
                leftSubtree.rsmRange.rsmTo,
                leftSubtree.inputRange.to)
        )

        return addNode(newIntermediateNode)
    }

}