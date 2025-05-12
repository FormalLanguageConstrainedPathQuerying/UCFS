package org.ucfs.sppf

import org.ucfs.sppf.node.*
import java.nio.file.Files
import java.nio.file.Path

fun <InputNode> writeSppfToDot(sppfNode: RangeSppfNode<InputNode>, filePath: String, label: String = "") {
    val genPath = Path.of("gen", "sppf")
    Files.createDirectories(genPath)
    val file = genPath.resolve(filePath).toFile()

    file.printWriter().use { out ->
        out.println(getSppfDot(sppfNode, label))
    }
}

fun <InputNode> getSppfDot(sppfNode: RangeSppfNode<InputNode>, label: String = ""): String {
    val queue: ArrayDeque<RangeSppfNode<InputNode>> = ArrayDeque(listOf(sppfNode))
    val visited: HashSet<Int> = HashSet()
    var node: RangeSppfNode<InputNode>
    val sb = StringBuilder()
    sb.appendLine("digraph g {")
    sb.appendLine("labelloc=\"t\"")
    sb.appendLine("label=\"$label\"")
    val nodeViews = HashMap<RangeSppfNode<InputNode>, String>()
    while (queue.isNotEmpty()) {
        node = queue.removeFirst()
        if (!visited.add(node.hashCode())) continue

        nodeViews[node] = getNodeView(node, )

        node.children.forEach {
            queue.addLast(it)
        }
    }
    val sortedViews = nodeViews.values.sorted()
    val nodeIds = HashMap<RangeSppfNode<InputNode>, Int>()
    val nodeById = HashMap<Int, RangeSppfNode<InputNode>>()
    for ((node, view) in nodeViews) {
        val id = sortedViews.indexOf(view)
        nodeIds[node] = id
        nodeById[id] = node
    }

    for (i in sortedViews.indices) {
        sb.appendLine("$i ${sortedViews[i]}")
    }

    for (i in nodeById.keys) {
        val node = nodeById[i]
        for(child in node!!.children) {
            sb.appendLine("${nodeIds[node]}->${nodeIds[child]}")
        }
       // if(node.children.size < 2){
       //     continue
       // }
       // val cs = node.children.map({nodeIds[it]}).joinToString("->")
       // sb.appendLine("{ rank = same; $cs [style=invis]}")
    }

    sb.appendLine("}")
    return sb.toString()

}


enum class NodeShape(val view: String) {
    Terminal("rectangle"), Nonterminal("invtrapezium"), Intermediate("plain"), Empty("ellipse"), Range("ellipse"), Epsilon(
        "invhouse"
    )
}

fun fillNodeTemplate(
    id: String? = null, nodeInfo: String, inputRange: InputRange<*>?, shape: NodeShape, rsmRange: RsmRange? = null
): String {
    val inputRangeView = if (inputRange != null) "input: [${inputRange.from}, ${inputRange.to}]" else null
    val rsmRangeView = if (rsmRange != null) "rsm: [${rsmRange.from.id}, ${rsmRange.to.id}]" else null
    val view = listOfNotNull(nodeInfo, inputRangeView, rsmRangeView).joinToString(", ")
    return "[label = \"${id?: ""}${shape.name} $view\", shape = ${shape.view}]"
}


fun <InputNode> getNodeView(node: RangeSppfNode<InputNode>, id: String? = null): String {
    val type = node.type
    return when (type) {
        is TerminalType<*> -> {
            fillNodeTemplate(
                id, "'${type.terminal}'", node.inputRange, NodeShape.Terminal
            )
        }

        is NonterminalType -> {
            fillNodeTemplate(
                id, "${type.startState.nonterminal.name}", node.inputRange, NodeShape.Nonterminal
            )
        }

        is IntermediateType<*> -> {
            fillNodeTemplate(
                id, "input: ${type.inputPosition}, rsm: ${type.grammarSlot.id}", node.inputRange, NodeShape.Intermediate
            )
        }

        is EmptyType -> {
            fillNodeTemplate(
                id, "", null, NodeShape.Empty
            )
        }

        is EpsilonNonterminalType -> {
            fillNodeTemplate(
                id, "RSM: ${type.startState.id}", node.inputRange, NodeShape.Epsilon
            )
        }

        is RangeType -> {
            fillNodeTemplate(
                id, "", node.inputRange, NodeShape.Range, node.rsmRange
            )
        }

        else -> throw IllegalStateException("Can't write node type $type to DOT")

    }


}

private fun getView(range: RsmRange?): String {
    if (range == null) return ""
    return "rsm: [(${range.from.nonterminal.name}:${range.from.numId}), " + "(${range.to.nonterminal.name}:${range.to.numId})]"
}