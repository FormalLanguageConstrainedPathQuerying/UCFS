package me.vkutuev

import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.extension.StringExtension.many
import org.ucfs.grammar.combinator.extension.StringExtension.or
import org.ucfs.grammar.combinator.extension.StringExtension.times
import org.ucfs.grammar.combinator.regexp.Nt
import org.ucfs.grammar.combinator.regexp.Option
import org.ucfs.grammar.combinator.regexp.many
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
    val R by Nt(Option("pt" * "pt_r") * many("assign_r"))

    init {
        S /= many( R * ("store_0" or "store_1" or "store_2" or "store_3")) * "pt"
    }
}

fun readGraph(name: String): InputGraph<Int, TerminalInputLabel> {
    val dotGraph = object {}.javaClass.getResource("/$name")?.readText()
        ?: throw RuntimeException("File $name not found in resources")
    val dotParser = DotParser()
    return dotParser.parseDot(dotGraph)
}

data class OutEgde(val start: Int, val symbol: String, val end: Int)

fun getPathFromSppf(node: RangeSppfNode<Int>, maxDepth: Int): List<List<OutEgde>>? {
    if (maxDepth == 0) {
        return null
    }
    when (val nodeType = node.type) {
        is TerminalType<*> -> {
            val range = node.inputRange ?: throw RuntimeException("Null inputRange for TerminalType node of SPPF")
            return listOf(listOf(OutEgde(range.from, nodeType.terminal.toString(), range.to)))
        }

        is NonterminalType if nodeType.startState.nonterminal.name == "R" -> {
            val range = node.inputRange ?: throw RuntimeException("Null inputRange for R Nonterminal node of SPPF")
            return listOf(listOf(OutEgde(range.from, "R", range.to)))
        }

        is EpsilonNonterminalType -> {
            return emptyList()
        }

        is EmptyType -> {
            throw RuntimeException("SPPF cannot contain EmptyRange")
        }

        is IntermediateType<*>, is NonterminalType -> {
            val subPaths = node.children.map { getPathFromSppf(it, maxDepth - 1) }
            if (subPaths.any { it == null }) {
                return null
            }
            val paths = subPaths.filterNotNull().fold(listOf(listOf<OutEgde>())) { acc, lst ->
                acc.flatMap { list -> lst.map { element -> list + element } }
            }
            return paths
        }

        is Range -> {
            val paths = node.children.map {
                getPathFromSppf(it, maxDepth - 1)?.filterNotNull()
            }.filterNotNull().flatten()
            if (paths.isEmpty()){return null}
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
    //listOf("graph_3.dot").forEach { graphName ->
        val graph = readGraph(graphName)
        val grammar = PointsToGrammar()
        val gll = Gll.gll(grammar.rsm, graph)
        val sppf = gll.parse()
        println("Founded paths in $graphName")
        sppf.forEach { getPathFromSppf(it, maxDepth = 30)?.forEach{
            println(it.toString())
        }
        }
        println()
        saveSppf(graphName, sppf)
    }
}
