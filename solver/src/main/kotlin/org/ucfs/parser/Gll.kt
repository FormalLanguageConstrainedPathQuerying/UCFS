package org.ucfs.parser

import org.ucfs.descriptors.Descriptor
import org.ucfs.gss.GssEdge
import org.ucfs.input.IInputGraph
import org.ucfs.input.ILabel
import org.ucfs.intersection.IIntersectionEngine
import org.ucfs.intersection.IntersectionEngine
import org.ucfs.parser.context.Context
import org.ucfs.rsm.RsmState
import org.ucfs.sppf.node.*

/**
 * Gll Factory
 * @param VertexType - type of vertex in input graph
 * @param LabelType - type of label on edges in input graph
 */
class Gll<VertexType, LabelType : ILabel> private constructor(
    override var ctx: Context<VertexType, LabelType>, private val engine: IIntersectionEngine
) : IGll<VertexType, LabelType> {

    companion object {
        /**
         * Creates instance of incremental Gll
         * @param startState - starting state of accepting nonterminal in RSM
         * @param inputGraph - input graph
         * @return default instance of gll parser
         */
        fun <VertexType, LabelType : ILabel> gll(
            startState: RsmState, inputGraph: IInputGraph<VertexType, LabelType>
        ): Gll<VertexType, LabelType> {
            return Gll(Context(startState, inputGraph), IntersectionEngine)
        }
    }

    private fun getEpsilonRange(descriptor: Descriptor<VertexType>): RangeSppfNode<VertexType> {
        val input = InputRange(
            descriptor.inputPosition,
            descriptor.inputPosition,
        )
        val rsm = RsmRange(
            descriptor.rsmState,
            descriptor.rsmState,
        )
        return ctx.sppfStorage.addEpsilonNode(input, rsm, descriptor.gssNode.rsm)
    }

    private fun handlePoppedGssEdge(
        poppedGssEdge: GssEdge<VertexType>, descriptor: Descriptor<VertexType>, childSppf: RangeSppfNode<VertexType>
    ) {
        val leftRange = poppedGssEdge.matchedRange
        val startRsmState = if (poppedGssEdge.matchedRange.type is EmptyType) poppedGssEdge.gssNode.rsm
        else poppedGssEdge.matchedRange.rsmRange!!.to
        val rightRange = ctx.sppfStorage.addNonterminalNode(
            InputRange(
                descriptor.gssNode.inputPosition, descriptor.inputPosition
            ), RsmRange(
                startRsmState,
                poppedGssEdge.state,
            ), descriptor.gssNode.rsm, childSppf
        )
        val newRange = ctx.sppfStorage.addIntermediateNode(leftRange, rightRange)
        val newDescriptor = Descriptor(
            descriptor.inputPosition, poppedGssEdge.gssNode, poppedGssEdge.state, newRange
        )
        ctx.descriptors.add(newDescriptor)
    }

    /**
     * Processes descriptor
     * @param descriptor - descriptor to process
     */
    override fun handleDescriptor(descriptor: Descriptor<VertexType>) {
        ctx.descriptors.addToHandled(descriptor)
        println("input: ${descriptor.inputPosition}")
        println("gss: ${descriptor.gssNode.id}")
        println("rsm: ${descriptor.rsmState.id}")
        println("sppf: ${descriptor.sppfNode.id}")
        if (descriptor.rsmState.isFinal) {
            val matchedRange = if (descriptor.sppfNode.type is EmptyType) {
                val node = getEpsilonRange(descriptor)
                //TODO fix
                // dirty hack: in fact it's equivavelnt descriptors
                // but only initial was added in handlet set
                ctx.descriptors.addToHandled(Descriptor(descriptor.inputPosition,
                    descriptor.gssNode, descriptor.rsmState, node))
                node
            } else {
                descriptor.sppfNode
            }
            for (poppedEdge in ctx.gss.pop(descriptor, matchedRange)) {
                handlePoppedGssEdge(poppedEdge, descriptor, matchedRange)
            }
            if (descriptor.gssNode.outgoingEdges.isEmpty() && descriptor.gssNode.rsm.isStart) {
                ctx.parseResult = matchedRange
            }
        }

        engine.handleEdges(this, descriptor)
    }
}

