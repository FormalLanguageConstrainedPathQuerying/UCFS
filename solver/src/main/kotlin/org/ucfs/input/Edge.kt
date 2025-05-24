package org.ucfs.input

data class Edge<VertexType>(
    val label: LightSymbol,
    val targetVertex: VertexType,
)
