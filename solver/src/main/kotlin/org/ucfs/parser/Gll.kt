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
        return RangeSppfNode(
            InputRange(
                descriptor.inputPosition,
                descriptor.inputPosition,
            ),
            RsmRange(
                descriptor.rsmState,
                descriptor.rsmState,
            ),
            EpsilonNonterminalType(descriptor.gssNode.rsm)
        )
    }

    private fun handlePoppedGssEdge(poppedGssEdge: GssEdge<VertexType>, descriptor: Descriptor<VertexType>){
        val leftRange = poppedGssEdge.matchedRange
        val startRsmState =
            if (poppedGssEdge.matchedRange.type == EmptyType)
                poppedGssEdge.gssNode.rsm
            else
                poppedGssEdge.matchedRange.rsmRange!!.rsmTo
        val rightEdge = RangeSppfNode(
            InputRange(
                descriptor.gssNode.inputPosition,
                descriptor.inputPosition
            ),
            RsmRange(
                startRsmState,
                poppedGssEdge.state,
            ),
            NonterminalType(descriptor.gssNode.rsm)
        )
        ctx.sppfStorage.addNode(rightEdge)
        val newRange = ctx.sppfStorage.addNode(leftRange, rightEdge)
        val newDescriptor = Descriptor(
            descriptor.inputPosition,
            poppedGssEdge.gssNode,
            poppedGssEdge.state,
            newRange
        )
        ctx.descriptors.add(newDescriptor)
    }
    /**
     * Processes descriptor
     * @param descriptor - descriptor to process
     */
    override fun handleDescriptor(descriptor: Descriptor<VertexType>) {
        ctx.descriptors.addToHandled(descriptor)
        if(descriptor.rsmState.isFinal){
            val matchedRange = if(descriptor.sppfNode.type == EmptyType) getEpsilonRange(descriptor) else descriptor.sppfNode
            val gssEdges = ctx.gss.pop(descriptor, matchedRange)
            gssEdges.map{::handlePoppedGssEdge}
            if(descriptor.rsmState == ctx.startState){
                ctx.parseResult = matchedRange
            }
        }

        engine.handleEdges(this, descriptor)
    }
}

