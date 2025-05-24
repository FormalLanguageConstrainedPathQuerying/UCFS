package org.ucfs.input

import org.ucfs.rsm.symbol.Term

open class LinearInput<VertexType> : InputGraph<VertexType>() {
    override fun toString(): String {
        if (startVertices.isEmpty())
            {
                return "${this.javaClass}: empty"
            }
        var v: VertexType = startVertices.first()
        val sb = StringBuilder()
        while (v != null) {
            val e = edges[v]?.first() ?: break
            sb.append("\n")
            sb.append(e.label)
            v = e.targetVertex
        }
        return sb.toString()
    }

    companion object {
        /**
         * Split CharSequence into stream of strings, separated by space symbol
         */
        fun buildFromString(input: String): IInputGraph<Int> {
            val inputGraph = LinearInput<Int>()
            var curVertexId = 0

            inputGraph.addStartVertex(curVertexId)
            inputGraph.addVertex(curVertexId)

            for (x in input.trim().split(SPACE).filter { it.isNotEmpty() }) {
                val light = SymbolRegistry.registerTerminal(Term(x))
                inputGraph.addEdge(curVertexId, light, ++curVertexId)
                inputGraph.addVertex(curVertexId)
            }
            return inputGraph
        }

        const val SPACE = " "
    }
}
