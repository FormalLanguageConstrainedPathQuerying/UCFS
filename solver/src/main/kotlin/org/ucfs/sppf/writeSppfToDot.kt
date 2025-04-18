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
    val edges: HashMap<Int, HashSet<Int>> = HashMap()
    val visited: HashSet<Int> = HashSet()
    var node: RangeSppfNode<InputNode>
    val sb = StringBuilder()
    sb.append("digraph g {")
    sb.append("labelloc=\"t\"")
    sb.append("label=\"$label\"")

    while (queue.isNotEmpty()) {
        node = queue.removeFirst()
        if (!visited.add(node.hashCode())) continue

        sb.append("\n")
        sb.append(printNode(node.hashCode(), node))

        node.children.forEach {
            queue.addLast(it)
            if (!edges.containsKey(node.hashCode())) {
                edges[node.hashCode()] = HashSet()
            }
            edges.getValue(node.hashCode()).add(it.hashCode())
        }
    }
    for ((head, tails) in edges) {
        for (tail in tails) {
            sb.append(printEdge(head, tail))
        }
    }
    sb.append("}")
    return sb.toString()

}


fun printEdge(x: Int, y: Int): String {
    return "${x}->${y}"
}

fun <InputNode>printNode(nodeId: Int, node: RangeSppfNode<InputNode>): String {
    val type = node.type
    return when (type) {
        is TerminalType<*> -> {
            "${nodeId} [label = \"Terminal ${nodeId} ; ${type.terminal }," +
                    " ${node.inputRange?.from}, ${node.inputRange?.to} \", shape = ellipse ]"
        }

        is NonterminalType -> {
            "${nodeId} [label = \"Symbol ${nodeId} ; ${type.startState.nonterminal.name}," +
                    " ${node.inputRange?.from}, ${node.inputRange?.to}, shape = octagon]"
        }

        is IntermediateType<*> -> {
            "${nodeId} [label = \"Intermediate ${nodeId} ; RSM: ${type.grammarSlot.nonterminal.name}, " +
                    "${node.inputRange?.from}, ${node.inputRange?.to}, shape = rectangle]"
        }
        is EmptyType -> {
            "${nodeId} [label = \"Range ${nodeId}\" ; shape = rectangle]"
        }

        is RangeType -> {
            "${nodeId} [label = \"Range ${nodeId} ; RSM: [${node.rsmRange!!.rsmFrom},${node.rsmRange.rsmTo}] \", " +
                    "${node.inputRange?.from}, ${node.inputRange?.to}, shape = rectangle]"
        }
        else -> throw IllegalStateException("Can't write node type $type to DOT")

    }
}