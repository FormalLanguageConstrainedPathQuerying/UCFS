package org.ucfs.intersection

import org.ucfs.descriptors.Descriptor
import org.ucfs.parser.IGll

interface IIntersectionEngine {
    fun <VertexType> handleEdges(
        gll: IGll<VertexType>,
        descriptor: Descriptor<VertexType>,
    )
}
