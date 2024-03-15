package org.srcgll

import org.srcgll.grammar.combinator.Grammar
import org.srcgll.grammar.combinator.regexp.*
import org.srcgll.input.Edge
import org.srcgll.input.IGraph
import org.srcgll.input.ILabel
import org.srcgll.input.LinearInputLabel
import org.srcgll.rsm.symbol.Terminal
import org.srcgll.rsm.writeRsmToDot
import org.srcgll.sppf.buildStringFromSppf
import org.srcgll.sppf.node.*
import org.srcgll.sppf.writeSppfToDot
import java.io.File

class Dyck : Grammar() {
    var S by Nt()

    init {
        S = Epsilon or "(" * S * ")" or S * S
        setStart(S)
    }
}
class SimpleGolang : Grammar() {

    var Program by Nt()
    var Block by Nt()
    var Statement by Nt()
    var IntExpr by Nt()

    init {
        Program = Block
        Block = Many(Statement)
        Statement = IntExpr * ";" or "r" * IntExpr * ";"
        IntExpr = "1" or "1" * "+" * "1"
        setStart(Program)
    }
}

/**
 * Define Class for a^n b^n Language CF-Grammar
 */
class AnBn : Grammar() {
    // Nonterminals
    var S by Nt()

    init {
        // Production rules. 'or' is Alternative, '*' is Concatenation
        S = Term("a") * Term("b") or Term("a") * S * Term("b")

        // Set Starting Nonterminal
        setStart(S)
    }
}

/**
 * Define Class for Stack Language CF-Grammar
 */
class Stack : Grammar() {
    // Nonterminals
    var S by Nt()

    init {
        // Production rules. 'or' is Alternative, '*' is Concatenation
        S = Many(
            Term("<-()") * Term("->()") or
            Term("<-.") * Term("->.") or
            Term("use_a") * Term("def_a") or
            Term("use_A") * Term("def_A") or
            Term("use_B") * Term("def_B") or
            Term("use_x") * Term("def_x") or
            Term("<-()") * S * Term("->()") or
            Term("<-.") * S * Term("->.") or
            Term("use_a") * S * Term("def_a") or
            Term("use_A") * S * Term("def_A") or
            Term("use_B") * S * Term("def_B") or
            Term("use_b") * S * Term("def_b") or
            Term("use_x") * S * Term("def_x")
        )

        // Set Starting Nonterminal
        setStart(S)
    }
}

/**
 * Realisation of ILabel interface which represents label on Input Graph edges
 */
class SimpleInputLabel(
    label: String?,
): ILabel {
    // null terminal represents epsilon edge in Graph
    override val terminal: Terminal<String>? = when (label) {
        null -> null
        else -> Terminal(label)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SimpleInputLabel) return false
        if (terminal != other.terminal) return false
        return true
    }
}
/**
 * Simple Realisation of IGraph interface as Directed Graph
 * @param VertexType   = Int
 * @param LabelType    = SimpleInputLabel
 */
class SimpleGraph: IGraph<Int, SimpleInputLabel> {
    override val vertices: MutableMap<Int, Int> = HashMap()
    override val edges: MutableMap<Int, MutableList<Edge<Int, SimpleInputLabel>>> = HashMap()

    override val startVertices: MutableSet<Int> = HashSet()

    override fun getInputStartVertices(): MutableSet<Int> = startVertices

    override fun isFinal(vertex: Int): Boolean = true

    override fun isStart(vertex: Int): Boolean = startVertices.contains(vertex)

    override fun removeEdge(from: Int, label: SimpleInputLabel, to: Int) {
        val edge = Edge(label, to)

        edges.getValue(from).remove(edge)
    }

    override fun addEdge(from: Int, label: SimpleInputLabel, to: Int) {
        val edge = Edge(label, to)
        if (!edges.containsKey(from)) edges[from] = ArrayList()
        edges.getValue(from).add(edge)
    }

    override fun getEdges(from: Int): MutableList<Edge<Int, SimpleInputLabel>> {
        return edges.getOrDefault(from, ArrayList())
    }

    override fun removeVertex(vertex: Int) {
        vertices.remove(vertex)
    }

    override fun addVertex(vertex: Int) {
        vertices[vertex] = vertex
    }

    override fun addStartVertex(vertex: Int) {
        startVertices.add(vertex)
    }

    override fun getVertex(vertex: Int?): Int? {
        return vertices.getOrDefault(vertex, null)
    }
}

fun createAnBnExampleGraph(startVertex: Int): SimpleGraph {
    val inputGraph = SimpleGraph()
    for (i in 0 .. 3) inputGraph.addVertex(vertex = i)

    inputGraph.addEdge(from = 0, to = 1, label = SimpleInputLabel("a"))
    inputGraph.addEdge(from = 1, to = 2, label = SimpleInputLabel("a"))
    inputGraph.addEdge(from = 2, to = 0, label = SimpleInputLabel("a"))
    inputGraph.addEdge(from = 0, to = 3, label = SimpleInputLabel("b"))
    inputGraph.addEdge(from = 3, to = 0, label = SimpleInputLabel("b"))

    // addStartVertex does not add Vertex to list of Vertices, so for starting vertices there should be both
    // calls to addVertex and addStartVertex
    inputGraph.addStartVertex(startVertex)

    return inputGraph
}

fun createStackExampleGraph(startVertex: Int): SimpleGraph {
    val inputGraph = SimpleGraph()

    inputGraph.addEdge(from = 0, to = 1, label = SimpleInputLabel("use_x"))
    inputGraph.addEdge(from = 1, to = 2, label = SimpleInputLabel("<-."))
    inputGraph.addEdge(from = 2, to = 3, label = SimpleInputLabel("<-()"))
    inputGraph.addEdge(from = 3, to = 33, label = SimpleInputLabel("use_B"))
    inputGraph.addEdge(from = 33, to = 32, label = SimpleInputLabel(null))
    inputGraph.addEdge(from = 4, to = 5, label = SimpleInputLabel("use_x"))
    inputGraph.addEdge(from = 5, to = 6, label = SimpleInputLabel("<-."))
    inputGraph.addEdge(from = 6, to = 32, label = SimpleInputLabel("use_B"))
    inputGraph.addEdge(from = 32, to = 31, label = SimpleInputLabel(null))
    inputGraph.addEdge(from = 13, to = 33, label = SimpleInputLabel("->."))
    inputGraph.addEdge(from = 14, to = 13, label = SimpleInputLabel("def_b"))
    inputGraph.addEdge(from = 31, to = 10, label = SimpleInputLabel("def_B"))
    inputGraph.addEdge(from = 10, to = 40, label = SimpleInputLabel("->."))
    inputGraph.addEdge(from = 10, to = 9, label = SimpleInputLabel("->()"))
    inputGraph.addEdge(from = 9, to = 41, label = SimpleInputLabel("->."))
    inputGraph.addEdge(from = 41, to = 40, label = SimpleInputLabel(null))
    inputGraph.addEdge(from = 41, to = 8, label = SimpleInputLabel("<-."))
    inputGraph.addEdge(from = 8, to = 7, label = SimpleInputLabel("<-()"))
    inputGraph.addEdge(from = 40, to = 7, label = SimpleInputLabel("<-."))
    inputGraph.addEdge(from = 7, to = 30, label = SimpleInputLabel("use_A"))
    inputGraph.addEdge(from = 30, to = 11, label = SimpleInputLabel("<-."))
    inputGraph.addEdge(from = 31, to = 30, label = SimpleInputLabel(null))
    inputGraph.addEdge(from = 11, to = 12, label = SimpleInputLabel("use_a"))
    inputGraph.addEdge(from = 12, to = 15, label = SimpleInputLabel(null))
    inputGraph.addEdge(from = 15, to = 16, label = SimpleInputLabel("def_a"))
    inputGraph.addEdge(from = 16, to = 22, label = SimpleInputLabel("->."))
    inputGraph.addEdge(from = 22, to = 17, label = SimpleInputLabel("def_A"))
    inputGraph.addEdge(from = 17, to = 18, label = SimpleInputLabel("->()"))
    inputGraph.addEdge(from = 17, to = 20, label = SimpleInputLabel("->."))
    inputGraph.addEdge(from = 18, to = 21, label = SimpleInputLabel("->."))
    inputGraph.addEdge(from = 21, to = 20, label = SimpleInputLabel(null))
    inputGraph.addEdge(from = 20, to = 19, label = SimpleInputLabel("def_x"))

    for ((vertexFrom, edges) in inputGraph.edges) {
        inputGraph.addVertex(vertexFrom)
        for (edge in edges) {
            inputGraph.addVertex(edge.head)
        }
    }

    inputGraph.addStartVertex(startVertex)

    return inputGraph
}

fun reachabilityExample() {
    val rsmAnBnStartState = AnBn().getRsm()
    val rsmStackStartState = Stack().getRsm()
    val startVertex = 0
    val inputGraphAnBn = createAnBnExampleGraph(startVertex)
    val inputGraphStack = createStackExampleGraph(startVertex)

    // result = (root of SPPF, set of reachable vertices)
    val resultAnBn: Pair<SppfNode<Int>?, HashMap<Pair<Int, Int>, Int>> =
        Gll(
            rsmAnBnStartState,
            inputGraphAnBn,
            recovery = RecoveryMode.OFF,
            reachability = ReachabilityMode.ALLPAIRS
        ).parse()
    val resultStack: Pair<SppfNode<Int>?, HashMap<Pair<Int, Int>, Int>> =
        Gll(
            rsmStackStartState,
            inputGraphStack,
            recovery = RecoveryMode.OFF,
            reachability = ReachabilityMode.ALLPAIRS
        ).parse()

    writeRsmToDot(rsmAnBnStartState, "test.dot")
    println("AnBn Language Grammar")
    println("Reachability pairs : ")

    resultAnBn.second.forEach { (pair, distance) ->
        println("from : ${pair.first} , to : ${pair.second} , distance : ${distance}")
    }

    println("\nStack Language Grammar")
    println("Reachability pairs : ")

    resultStack.second.forEach { (pair, distance) ->
        println("from : ${pair.first} , to : ${pair.second} , distance : ${distance}")
    }
}

fun gatherNodes(sppfNode: ISppfNode): HashSet<Int> {
    val queue: ArrayDeque<ISppfNode> = ArrayDeque(listOf(sppfNode))
    val created: HashSet<Int> = HashSet()
    var node: ISppfNode

    while (queue.isNotEmpty()) {
        node = queue.removeFirst()
        if (!created.add(node.id)) continue

        (node as? ParentSppfNode<*>)?.kids?.forEach {
            queue.addLast(it)
        }

        val leftChild = (node as? PackedSppfNode<*>)?.leftSppfNode
        val rightChild = (node as? PackedSppfNode<*>)?.rightSppfNode

        if (leftChild != null) {
            queue.addLast(leftChild)
        }
        if (rightChild != null) {
            queue.addLast(rightChild)
        }
    }

    return created
}

fun golangErrorRecoveryIncrementalityExample(input: String) {
    val rsm = SimpleGolang().getRsm()
    val inputGraph = getTokenStream(input)
    val gll = Gll(rsm, inputGraph, RecoveryMode.ON, reachability = ReachabilityMode.REACHABILITY)

    var result = gll.parse()

    writeSppfToDot(result.first!!, "./result.dot", "r 1 + ;")
    val recoveredString = buildStringFromSppf(result.first!!)

    val created = gatherNodes(result.first!!)
    var addFrom = 3
    val initEdges = inputGraph.getEdges(addFrom)

    inputGraph.edges.remove(addFrom)
    inputGraph.addEdge(addFrom, LinearInputLabel(Terminal("1")), 5)
    inputGraph.edges[5] = initEdges

    inputGraph.addVertex(5)
    var newResult = gll.parse(3)

    WriteSppfToDot(newResult.first!!,"./resultIncremental.dot", created,"r 1 + 1 ;")
}
fun WriteSppfToDot(sppfNode: ISppfNode, filePath: String, created: HashSet<Int>, label: String = "") {
    val queue: ArrayDeque<ISppfNode> = ArrayDeque(listOf(sppfNode))
    val edges: HashMap<Int, HashSet<Int>> = HashMap()
    val visited: HashSet<Int> = HashSet()
    var node: ISppfNode

    val file = File(filePath)

    file.printWriter().use { out ->
        out.println("digraph g {")
        out.println("labelloc=\"t\"")
        out.println("label=\"$label\"")

        while (queue.isNotEmpty()) {
            node = queue.removeFirst()
            if (!visited.add(node.id)) continue

            out.println(PrintNode(node.id, node, created))

            (node as? ParentSppfNode<*>)?.kids?.forEach {
                queue.addLast(it)
                if (!edges.containsKey(node.id)) {
                    edges[node.id] = HashSet()
                }
                edges.getValue(node.id).add(it.id)
            }

            val leftChild = (node as? PackedSppfNode<*>)?.leftSppfNode
            val rightChild = (node as? PackedSppfNode<*>)?.rightSppfNode

            if (leftChild != null) {
                queue.addLast(leftChild)
                if (!edges.containsKey(node.id)) {
                    edges[node.id] = HashSet()
                }
                edges.getValue(node.id).add(leftChild.id)
            }
            if (rightChild != null) {
                queue.addLast(rightChild)
                if (!edges.containsKey(node.id)) {
                    edges[node.id] = HashSet()
                }
                edges.getValue(node.id).add(rightChild.id)
            }
        }
        for (kvp in edges) {
            val head = kvp.key
            for (tail in kvp.value) out.println(PrintEdge(head, tail))
        }
        out.println("}")
    }
}

fun GetColor(set: HashSet<Int>, id: Int): String = if (set.contains(id)) "green" else "black"

fun PrintEdge(x: Int, y: Int): String {
    return "${x}->${y}"
}

fun PrintNode(nodeId: Int, node: ISppfNode, set: HashSet<Int>): String {
    return when (node) {
        is TerminalSppfNode<*> -> {
            "${nodeId} [label = \"${nodeId} ; ${node.terminal ?: "eps"}, ${node.leftExtent}, ${node.rightExtent}, Weight: ${node.weight}\", shape = ellipse, color = ${GetColor(set, nodeId)}]"
        }

        is SymbolSppfNode<*> -> {
            "${nodeId} [label = \"${nodeId} ; ${node.symbol.name}, ${node.leftExtent}, ${node.rightExtent}, Weight: ${node.weight}\", shape = octagon, color = ${GetColor(set, nodeId)}]"
        }

        is ItemSppfNode<*> -> {
            "${nodeId} [label = \"${nodeId} ; RSM: ${node.rsmState.nonterminal.name}, ${node.leftExtent}, ${node.rightExtent}, Weight: ${node.weight}\", shape = rectangle, color = ${GetColor(set, nodeId)}]"
        }

        is PackedSppfNode<*> -> {
            "${nodeId} [label = \"${nodeId} ; Weight: ${node.weight}\", shape = point, width = 0.5, color = ${GetColor(set, nodeId)}]"
        }

        else -> ""
    }
}

fun main() {
}

