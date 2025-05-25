package org.ucfs.parser

import org.ucfs.descriptors.Descriptor
import org.ucfs.input.Edge
import org.ucfs.input.IInputGraph
import org.ucfs.input.ILabel
import org.ucfs.parser.context.Context
import org.ucfs.rsm.RsmState
import org.ucfs.rsm.symbol.ITerminal
import org.ucfs.rsm.symbol.Nonterminal
import org.ucfs.sppf.node.*

/**
 * Interface for Gll parser
 * @param InputNodeType - type of vertex in input graph
 * @param LabelType - type of label on edges in input graph
 */
interface IGll<InputNodeType, LabelType : ILabel> {
    /**
     * Parser configuration
     */
    var ctx: Context<InputNodeType, LabelType>

    /**
     * Main parsing loop. Iterates over available descriptors and processes them
     * @return Pair of derivation tree root and collection of reachability pairs
     */
    fun parse(): RangeSppfNode<InputNodeType>? {
        ctx.parseResult = null
        initDescriptors(ctx.input)

        var curDescriptor = ctx.descriptors.nextToHandle()

        while (curDescriptor != null) {
            handleDescriptor(curDescriptor)
            curDescriptor = ctx.descriptors.nextToHandle()
        }

       // assert(ctx.parseResult != null)
       // assert(ctx.parseResult!!.children.size == 1)
        return ctx.parseResult!!.children.get(0)
    }

    /**
     * Processes descriptor
     * @param descriptor - descriptor to process
     */
    fun handleDescriptor(descriptor: Descriptor<InputNodeType>)

    /**
     * Creates descriptors for all starting vertices in input graph
     * @param input - input graph
     */
    fun initDescriptors(input: IInputGraph<InputNodeType, LabelType>) {
        for (startVertex in input.getInputStartVertices()) {

            val gssNode = ctx.gss.getOrCreateNode(startVertex, ctx.fictiveStartState)
            val startDescriptor = Descriptor(
                startVertex, gssNode, ctx.fictiveStartState, getEmptyRange(true)
            )
            ctx.descriptors.add(startDescriptor)
        }
    }

    fun handleNonterminalEdge(
        descriptor: Descriptor<InputNodeType>, destinationRsmState: RsmState, edgeNonterminal: Nonterminal
    ) {
        val rsmStartState = edgeNonterminal.startState
        val (newGssNode, positionToPops) = ctx.gss.addEdge(
            descriptor.gssNode, destinationRsmState, descriptor.inputPosition, rsmStartState, descriptor.sppfNode
        )

        var newDescriptor = Descriptor(
            descriptor.inputPosition, newGssNode, rsmStartState, getEmptyRange()
        )
        ctx.descriptors.add(newDescriptor)

        for (rangeToPop in positionToPops) {
            val leftSubRange = descriptor.sppfNode
            val rightSubRange = ctx.sppfStorage.addNonterminalNode(
                    rangeToPop.inputRange!!, RsmRange(
                        descriptor.rsmState, destinationRsmState
                    ), rsmStartState
                )

            val newSppfNode = ctx.sppfStorage.addIntermediateNode(leftSubRange, rightSubRange)

            //TODO why these parameters???
            newDescriptor = Descriptor(
                rangeToPop.inputRange!!.to, descriptor.gssNode, destinationRsmState, newSppfNode
            )
            ctx.descriptors.add(newDescriptor)
        }
    }


    fun handleTerminalEdge(
        descriptor: Descriptor<InputNodeType>,
        inputEdge: Edge<InputNodeType, *>,
        destinationRsmState: RsmState,
        terminal: ITerminal
    ) {
        var terminalSppfNode = ctx.sppfStorage.addNode(
            InputRange(
                descriptor.inputPosition,
                inputEdge.targetVertex,
            ), RsmRange(
                descriptor.rsmState,
                destinationRsmState,
            ), terminal
        )
        val intermediateOrTerminalSppf = ctx.sppfStorage.addIntermediateNode(descriptor.sppfNode, terminalSppfNode)
        val descriptorForTerminal = Descriptor(
            inputEdge.targetVertex, descriptor.gssNode, destinationRsmState, intermediateOrTerminalSppf
        )
        ctx.descriptors.add(descriptorForTerminal)
    }
}
