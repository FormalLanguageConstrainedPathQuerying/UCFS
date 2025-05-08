package org.ucfs.gss

import org.ucfs.rsm.RsmState
import org.ucfs.sppf.node.RangeSppfNode

data class GssEdge<VertexType> (val gssNode: GssNode<VertexType>, val state: RsmState, val matchedRange: RangeSppfNode<VertexType>){

}