package org.ucfs.parser.context

import org.ucfs.descriptors.DescriptorsStorage
import org.ucfs.gss.GraphStructuredStack
import org.ucfs.input.IInputGraph
import org.ucfs.input.ILabel
import org.ucfs.rsm.RsmState
import org.ucfs.sppf.SppfStorage
import org.ucfs.sppf.ReachabilitySppfStorage
import org.ucfs.sppf.node.RangeSppfNode


/**
 * @param InputNodeType - type of vertex in input graph
 * @param LabelType - type of label on edges in input graph
 */
class Context<InputNodeType, LabelType : ILabel>(
    /**
     * Starting state of accepting Nonterminal in RSM
     */
    val fictiveStartState: RsmState,
    val fictiveFinalState: RsmState,
    val input: IInputGraph<InputNodeType, LabelType>,
    /**
     * Derivation trees storage ([org.ucfs.sppf.ReachabilitySppfStorage] keeps only range nodes)
     */
    val sppfStorage: SppfStorage<InputNodeType> = SppfStorage(),
) {
    private val continuationCache = HashMap<RsmState, HashMap<InputNodeType, Boolean>>()

    /** A terminal-only return state with no matching outgoing input edge cannot produce a result. */
    fun canContinueAt(state: RsmState, position: InputNodeType): Boolean {
        if (sppfStorage !is ReachabilitySppfStorage<*> || state.isFinal || state.nonterminalEdgesStorage.isNotEmpty()) {
            return true
        }
        if (state.terminalEdgesStorage.isEmpty()) return false
        val byPosition = continuationCache.getOrPut(state) { HashMap() }
        byPosition[position]?.let { return it }
        val viable = input.getEdges(position).any { edge ->
            val terminal = edge.label.terminal
            terminal != null && state.getTerminalEdge(terminal) != null
        }
        byPosition[position] = viable
        return viable
    }

    fun resetContinuationCache() = continuationCache.clear()

    /**
     * Collection of descriptors
     */
    val descriptors: DescriptorsStorage<InputNodeType> = DescriptorsStorage()


    val gss: GraphStructuredStack<InputNodeType> = GraphStructuredStack()



    var parseResults = HashSet<RangeSppfNode<InputNodeType>>()
}
