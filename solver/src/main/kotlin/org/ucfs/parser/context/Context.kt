package org.ucfs.parser.context

import org.ucfs.descriptors.DescriptorsStorage
import org.ucfs.gss.GraphStructuredStack
import org.ucfs.input.IInputGraph
import org.ucfs.input.ILabel
import org.ucfs.rsm.RsmState
import org.ucfs.sppf.SppfStorage
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

    /**
     * Collection of descriptors
     */
    val descriptors: DescriptorsStorage<InputNodeType> = DescriptorsStorage()


    val gss: GraphStructuredStack<InputNodeType> = GraphStructuredStack()



    var parseResults = HashSet<RangeSppfNode<InputNodeType>>()
}