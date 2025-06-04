package org.ucfs.descriptors

import org.ucfs.gss.GssNode
import org.ucfs.rsm.RsmState
import org.ucfs.sppf.node.RangeSppfNode

/**
 * Descriptor represents current parsing stage
 * @param InputNodeType - type of vertex in input graph
 */
data class Descriptor<InputNodeType>(
    /**
     * Pointer to vertex in input graph
     */
    val inputPosition: InputNodeType,
    /**
     * Pointer to node in top layer of graph structured stack
     */
    val gssNode: GssNode<InputNodeType>,
    /**
     * State in RSM, corresponds to slot in CF grammar
     */
    val rsmState: RsmState,
    /**
     * Pointer to already parsed portion of input, represented as derivation tree, which shall be connected afterwards
     * to derivation trees, stored on edges of GSS, it corresponds to return from recursive function
     */
    val sppfNode: RangeSppfNode<InputNodeType>,


) {
    // debug only property
    val id = lastId++
    override fun toString(): String {
        return "${id}\t;" +
                "${inputPosition}\t;" +
                "${rsmState.id}\t;" +
                "(${gssNode.inputPosition}, ${gssNode.rsm.id})\t;" +
                "sppf: ${sppfNode.id} "
    }
}

var lastId = 0
