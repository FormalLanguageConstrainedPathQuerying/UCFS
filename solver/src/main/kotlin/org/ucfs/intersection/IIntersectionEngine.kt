package org.ucfs.intersection

import org.ucfs.descriptors.Descriptor
import org.ucfs.input.ILabel
import org.ucfs.parser.IGll

interface IIntersectionEngine {
    fun <VertexType, LabelType : ILabel> handleEdges(
        gll: IGll<VertexType, LabelType>,
        descriptor: Descriptor<VertexType>,
    )
}