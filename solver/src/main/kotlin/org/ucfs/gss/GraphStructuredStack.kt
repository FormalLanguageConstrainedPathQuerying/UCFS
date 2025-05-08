package org.ucfs.gss

import org.ucfs.descriptors.Descriptor
import org.ucfs.rsm.RsmState
import org.ucfs.sppf.node.RangeSppfNode

class GraphStructuredStack<InputNode> {
    val nodes = ArrayList<GssNode<InputNode>>()

    fun getOrCreateNode(input: InputNode, rsm: RsmState): GssNode<InputNode> {
        val node = findNode(input, rsm)
        return if (node == null) GssNode(rsm, input) else node
    }

    fun findNode(input: InputNode, rsm: RsmState): GssNode<InputNode>? {
        return nodes.find { node -> node.inputPosition == input && node.rsm == rsm }
    }

    fun addEdge(
        gssNode: GssNode<InputNode>,
        rsmStateToReturn: RsmState,
        inputToContinue: InputNode,
        rsmStateToContinue: RsmState,
        matcherRange: RangeSppfNode<InputNode>
    ): GssResult<InputNode> {
        val addedNode = getOrCreateNode(inputToContinue, rsmStateToContinue)
        val edge = GssEdge(gssNode, rsmStateToReturn, matcherRange)


        // There is no need to check GSS edges duplication.
        // "Faster, Practical GLL Parsing", Ali Afroozeh and Anastasia Izmaylova
        // p.13: "There is at most one call to the create function with the same arguments.
        // Thus no check for duplicate GSS edges is needed."
        val popped = addedNode.addEdge(edge)
        return GssResult(addedNode, popped)
    }


    /**
     * return outgoing edges
     */
    fun pop(
        descriptor: Descriptor<InputNode>, range: RangeSppfNode<InputNode>
    ): ArrayList<GssEdge<InputNode>> {
        val gssNode = descriptor.gssNode
        gssNode.popped.add(range)
        return gssNode.outgoingEdges
    }

}

data class GssResult<InputNodeType>(
    val gssNode: GssNode<InputNodeType>, val popped: ArrayList<RangeSppfNode<InputNodeType>>
)


