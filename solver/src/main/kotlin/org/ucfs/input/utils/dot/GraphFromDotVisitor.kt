package org.ucfs.input.utils.dot

import org.ucfs.input.InputGraph
import org.ucfs.input.LightSymbol
import org.ucfs.input.SymbolRegistry
import org.ucfs.rsm.symbol.Term

class GraphFromDotVisitor : DotBaseVisitor<InputGraph<Int>>() {
    lateinit var graph: InputGraph<Int>

    override fun visitGraph(ctx: DotParser.GraphContext?): InputGraph<Int> {
        graph = InputGraph()
        super.visitGraph(ctx)
        ctx?.id_()?.let { graph.name = it.text }
        return graph
    }

    private fun getNodeId(vertexView: String): Int {
        return vertexView.toInt()
    }

    private fun parseSimpleEdge(edgeView: String): LightSymbol {
        val viewWithoutQuotes = edgeView.substring(1, edgeView.length - 1)
        return SymbolRegistry.registerTerminal(Term(viewWithoutQuotes))
    }

    override fun visitEdge_stmt(ctx: DotParser.Edge_stmtContext?): InputGraph<Int> {
        val tos =
            ctx?.edgeRHS()?.node_id()
                // we don't handle subgraph here
                ?: return super.visitEdge_stmt(ctx)
        if (tos.size > 1) {
            throw Exception("we can't handle transitives in dot yet!")
        }
        val to = getNodeId(tos[0].text)
        if (ctx.node_id()?.text == "start") {
            graph.addVertex(to)
            graph.addStartVertex(to)
        } else {
            val from = getNodeId(ctx.node_id().text)
            val attrs = ctx.attr_list().attr() ?: throw Exception("we can't handle edges without labels yet!")

            val labelNode =
                attrs.find { it.label_name.text == "label" }
                    ?: throw Exception("we can't handle edges without labels yet!")
            graph.addVertex(from)
            graph.addVertex(to)
            graph.addEdge(from, parseSimpleEdge(labelNode.label_value.text), to)
        }
        super.visitEdge_stmt(ctx)
        return graph
    }

    override fun visitNode_stmt(ctx: DotParser.Node_stmtContext?): InputGraph<Int> {
        if (ctx?.node_id()?.text == "start") {
            return super.visitNode_stmt(ctx)
        }
        // add node info
        super.visitNode_stmt(ctx)
        return graph
    }
}
