package org.ucfs.intersection

import org.ucfs.descriptors.Descriptor
import org.ucfs.input.ILabel
import org.ucfs.parser.IGll
import org.ucfs.rsm.symbol.ITerminal
import org.ucfs.rsm.symbol.Nonterminal

object IntersectionEngine : IIntersectionEngine {


    /**
     * Process outgoing edges from input position in given descriptor, according to processing logic, represented as
     * separate functions for both outgoing terminal and nonterminal edges from rsmState in descriptor
     * @param gll - Gll parser instance
     * @param descriptor - descriptor, represents current parsing stage
     */
    override fun <VertexType, LabelType : ILabel> handleEdges(
        gll: IGll<VertexType, LabelType>,
        descriptor: Descriptor<VertexType>,
    ) {

        for (inputEdge in gll.ctx.input.getEdges(descriptor.inputPosition)) {
            val inputTerminal = inputEdge.label.terminal
            val rsmEdge = descriptor.rsmState.terminalEdgesStorage.find {
                it.symbol == inputTerminal
            }
            if (rsmEdge != null) {
                gll.handleTerminalEdge(
                    descriptor, inputEdge, rsmEdge.destinationState, rsmEdge.symbol as ITerminal
                )
            }
        }

        for (nonterminalEdge in descriptor.rsmState.nonterminalEdgesStorage) {
            gll.handleNonterminalEdge(
                descriptor, nonterminalEdge.destinationState, nonterminalEdge.symbol as Nonterminal
            )
        }
    }
}

