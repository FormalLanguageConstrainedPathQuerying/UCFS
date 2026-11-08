package org.ucfs.descriptors

import org.ucfs.gss.GssNode
import org.ucfs.rsm.RsmState
import org.ucfs.sppf.node.EmptyType
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
    // Nonempty SPPF nodes are canonicalized by SppfStorage. Empty nodes are
    // created at each parser entry and remain equal by value.
    private val sppfHash = if (sppfNode.type is EmptyType) 12 else System.identityHashCode(sppfNode)
    private val cachedHash = (((inputPosition?.hashCode() ?: 0) * 31 + gssNode.hashCode()) * 31 +
            rsmState.hashCode()) * 31 + sppfHash

    override fun hashCode() = cachedHash

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Descriptor<*>) return false
        return inputPosition == other.inputPosition &&
                gssNode == other.gssNode &&
                rsmState == other.rsmState &&
                (sppfNode === other.sppfNode ||
                        (sppfNode.type is EmptyType && other.sppfNode.type is EmptyType))
    }

    // debug only property
    val id = lastId++
    override fun toString(): String {
        return "${id}\t;" +
                "${inputPosition}\t;" +
                "${rsmState.id}\t;" +
                "(${gssNode.inputPosition}, ${gssNode.rsm.id})\t;"
    }
}

var lastId = 0
