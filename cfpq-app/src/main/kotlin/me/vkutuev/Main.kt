package me.vkutuev

import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.extension.StringExtension.many
import org.ucfs.grammar.combinator.extension.StringExtension.or
import org.ucfs.grammar.combinator.extension.StringExtension.times
import org.ucfs.grammar.combinator.regexp.Nt
import org.ucfs.grammar.combinator.regexp.Option
import org.ucfs.grammar.combinator.regexp.many
import org.ucfs.grammar.combinator.regexp.or
import org.ucfs.input.DotParser
import org.ucfs.input.InputGraph
import org.ucfs.input.TerminalInputLabel
import org.ucfs.parser.Gll
import org.ucfs.sppf.getSppfDot
import org.ucfs.sppf.node.*
import java.nio.file.Files
import java.nio.file.Path

class PointsToGrammar : Grammar() {
    val S by Nt().asStart()
    val Alias by Nt()
    val PointsTo by Nt(many("assign" or ("load_0" * Alias * "store_0") 
                                     or ("load_1" * Alias * "store_1") 
                                     or ("load_2" * Alias * "store_2") 
                                     or ("load_3" * Alias * "store_3")
                           ) * "alloc")
    val FlowsTo by Nt("alloc_r" * many("assign_r" or ("store_0_r" * Alias * "load_0_r")
                                                  or ("store_1_r" * Alias * "load_1_r")
                                                  or ("store_2_r" * Alias * "load_2_r")
                                                  or ("store_3_r" * Alias * "load_3_r")))
    
    init {
        Alias /= PointsTo * FlowsTo
        S /= many( Option(Alias) * ("store_0" or "store_1" or "store_2" or "store_3")) * PointsTo
    }
}

fun readGraph(name: String): InputGraph<Int, TerminalInputLabel> {
    val dotGraph = object {}.javaClass.getResource("/$name")?.readText()
        ?: throw RuntimeException("File $name not found in resources")
    val dotParser = DotParser()
    return dotParser.parseDot(dotGraph)
}

data class OutEdge(val start: Int, val symbol: String, val end: Int){
    override fun toString(): String = "(" + start.toString() + "-" + symbol + "->" + end.toString() + ")"
}

fun getPathFromSppf(node: RangeSppfNode<Int>, maxDepth: Int): List<List<OutEdge>>? {
    if (maxDepth == 0) {
        return null
    }
    when (val nodeType = node.type) {
        is TerminalType<*> -> {
            val range = node.inputRange ?: throw RuntimeException("Null inputRange for TerminalType node of SPPF")
            return listOf(listOf(OutEdge(range.from, nodeType.terminal.toString(), range.to)))
        }

        //Do not extract subpaths for non-terminal Alias because they are useless.
        is NonterminalType if nodeType.startState.nonterminal.name == "Alias" -> {
            val range = node.inputRange ?: throw RuntimeException("Null inputRange for Alias Nonterminal node of SPPF")
            return listOf(listOf(OutEdge(range.from, "Alias", range.to)))
        }

        //Do not extract subpaths for non-terminal PointsTo because they are useless.
        is NonterminalType if nodeType.startState.nonterminal.name == "PointsTo" -> {
            val range = node.inputRange ?: throw RuntimeException("Null inputRange for PointsTo Nonterminal node of SPPF")
            return listOf(listOf(OutEdge(range.from, "PointsTo", range.to)))
        }

        is EpsilonNonterminalType -> {
            return listOf(emptyList())
        }

        is EmptyType -> {
            throw RuntimeException("SPPF cannot contain EmptyRange")
        }

        is IntermediateType<*>, is NonterminalType -> {
            val subPaths = node.children.map { getPathFromSppf(it, maxDepth - 1) }
            if (subPaths.any { it == null }) {
                return null
            }
            val paths = subPaths.filterNotNull().fold(listOf(listOf<OutEdge>())) { acc, lst ->
                acc.flatMap { list -> lst.map { element -> list + element } }
            }
            return paths
        }

        is Range -> {
            val paths = node.children.map {
                getPathFromSppf(it, maxDepth - 1)?.filterNotNull()
            }.filterNotNull().flatten()
            if (paths.isEmpty()) {
                return null
            }
            return paths
        }

        else -> {
            println("Type of node is ${node.type.javaClass}")
            throw RuntimeException("Unknown RangeType in SPPF")
        }
    }
}

fun saveSppf(name: String, sppf: Set<RangeSppfNode<Int>>) {
    val graphName = name.removeSuffix(".dot")
    val genPath = Path.of("gen", "sppf")
    Files.createDirectories(genPath)
    val file = genPath.resolve("${graphName}_sppf.dot").toFile()

    file.printWriter().use { out ->
        out.println(getSppfDot(sppf))
    }
}

fun main() {
    listOf("graph_1.dot", "graph_2.dot", "graph_3.dot", "graph_4.dot").forEach { graphName ->
        val graph = readGraph(graphName)
        val grammar = PointsToGrammar()
        val gll = Gll.gll(grammar.rsm, graph)
        val sppf = gll.parse()
        println("Founded paths in $graphName")
        sppf.forEach { getPathFromSppf(it, maxDepth = 30)?.forEach { println(it.toString()) } }
        println()
        saveSppf(graphName, sppf)
    }
}
