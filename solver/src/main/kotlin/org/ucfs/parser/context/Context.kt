package org.ucfs.parser.context

import org.ucfs.descriptors.DescriptorsStorage
import org.ucfs.gss.GraphStructuredStack
import org.ucfs.input.IInputGraph
import org.ucfs.rsm.RsmState
import org.ucfs.sppf.SppfStorage
import org.ucfs.sppf.node.RangeSppfNode

/**
 * @param InputNodeType - type of vertex in input graph
 * @param LabelType - type of label on edges in input graph
 */
class Context<InputNodeType>(
    /**
     * Starting state of accepting Nonterminal in RSM
     */
    val startState: RsmState,
    val input: IInputGraph<InputNodeType>,
) {
    /**
     * Collection of descriptors
     */
    val descriptors: DescriptorsStorage<InputNodeType> = DescriptorsStorage(input.verticesNumber() * 3)

    /**
     * Derivation trees storage
     */
    val sppfStorage: SppfStorage<InputNodeType> = SppfStorage()

    val gss: GraphStructuredStack<InputNodeType> = GraphStructuredStack(input.verticesNumber() * 3)

    var parseResult: RangeSppfNode<InputNodeType>? = null
}
