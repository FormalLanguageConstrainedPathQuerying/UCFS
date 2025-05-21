package org.ucfs.descriptors

import org.ucfs.gss.GssNode
import org.ucfs.rsm.RsmState
import org.ucfs.sppf.node.RangeSppfNode
import java.util.Objects

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
    private val hash = Objects.hash(inputPosition, gssNode, rsmState, sppfNode)

    override fun hashCode() = hash
}
