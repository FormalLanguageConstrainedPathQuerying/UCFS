package org.ucfs.sppf

import org.ucfs.sppf.node.*
import java.nio.file.Files
import java.nio.file.Path

val printWithId = false
fun <InputNode> writeSppfToDot(sppfNode: RangeSppfNode<InputNode>, filePath: String, label: String = "") {
    val genPath = Path.of("gen", "sppf")
    Files.createDirectories(genPath)
    val file = genPath.resolve(filePath).toFile()

    file.printWriter().use { out ->
        out.println(getSppfDot(sppfNode, label))
    }
}

fun <InputNode> getSppfDot(sppfNodes: Set<RangeSppfNode<InputNode>>, label: String = ""): String {
    val sb = StringBuilder()
    sb.appendLine("digraph g {")
    sb.appendLine("labelloc=\"t\"")
    sb.appendLine("label=\"$label\"")
    var idx = 0
    val results = sppfNodes.sortedWith(compareBy { it.toString() }).map { sppf -> getSppfDot(sppf.children[0], idx++.toString()) }
    for (sppf in results.sorted()) {
        sb.appendLine(sppf)
    }
    sb.appendLine("}")
    return sb.toString()
}

fun <InputNode> getSppfDot(sppfNode: RangeSppfNode<InputNode>, label: String): String {
    val prefix = "_${label}_"
    val queue: ArrayDeque<RangeSppfNode<InputNode>> = ArrayDeque(listOf(sppfNode))
    val visited: HashSet<Int> = HashSet()
    var node: RangeSppfNode<InputNode>
    val sb = StringBuilder()
    sb.appendLine("subgraph cluster_$label{")
    sb.appendLine("labelloc=\"t\"")
    val nodeViews = HashMap<RangeSppfNode<InputNode>, String>()
    while (queue.isNotEmpty()) {
        node = queue.removeFirst()
        if (!visited.add(node.hashCode())) continue

        nodeViews[node] = getNodeView(node, node.id.toString())

        node.children.forEach {
            queue.addLast(it)
        }
    }
    val sortedViews = nodeViews.values.sorted()
    val nodeIds = HashMap<RangeSppfNode<InputNode>, Int>()
    val nodeById = HashMap<Int, RangeSppfNode<InputNode>>()
    for ((nodeInstance, view) in nodeViews) {
        val id = sortedViews.indexOf(view)
        nodeIds[nodeInstance] = id
        nodeById[id] = nodeInstance
    }

    for (i in sortedViews.indices) {
        sb.appendLine("$prefix$i ${sortedViews[i]}")
    }

    for (i in nodeById.keys) {
        val sortedNode = nodeById[i]
        for (child in sortedNode!!.children) {
            sb.appendLine("$prefix${nodeIds[sortedNode]}->$prefix${nodeIds[child]}")
        }
        // TODO change format to force DOT to save children order
    }

    sb.appendLine("}")
    return sb.toString()
}


enum class NodeShape(val view: String) {
    Terminal("rectangle"), Nonterminal("invtrapezium"), Intermediate("plain"), Empty("ellipse"), Range("ellipse"), Epsilon(
        "invhouse"
    )
}

fun getNodeView(node: RangeSppfNode<*>, id: String): String {
    val shape = getNodeShape(node.type)
    val idView = if (printWithId) "$id " else ""
    return "[label = \"$idView${node}\", shape = ${shape.view}]"
}

fun getNodeShape(rangeType: RangeType): NodeShape {
    return when (rangeType) {
        is TerminalType<*> -> NodeShape.Terminal
        is NonterminalType -> NodeShape.Nonterminal
        is IntermediateType<*> -> NodeShape.Intermediate
        is EmptyType -> NodeShape.Empty
        is EpsilonNonterminalType -> NodeShape.Epsilon
        is Range -> NodeShape.Range
        else -> throw IllegalStateException("Can't write node type $rangeType to DOT")
    }
}
