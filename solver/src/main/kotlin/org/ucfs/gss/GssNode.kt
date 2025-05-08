package org.ucfs.gss

import org.ucfs.descriptors.Descriptor
import org.ucfs.rsm.RsmState
import org.ucfs.sppf.node.RangeSppfNode
import java.util.*
import kotlin.collections.ArrayList

/**
 * Node in Graph Structured Stack
 * @param InputNodeType - type of vertex in input graph
 */
var lastId = 0

data class GssNode<InputNodeType>(
    /**
     * RSM corresponding to grammar slot
     */
    val rsm: RsmState,
    /**
     * Pointer to vertex in input graph
     */
    val inputPosition: InputNodeType, val id: Int = lastId++

) {
    val popped = ArrayList<RangeSppfNode<InputNodeType>>()

    val outgoingEdges = ArrayList<GssEdge<InputNodeType>>()

    /**
     * Add edge and return popped
     */
    fun addEdge(edge: GssEdge<InputNodeType>): ArrayList<RangeSppfNode<InputNodeType>> {
        outgoingEdges.add(edge)
        //TODO
        return popped
    }

}
