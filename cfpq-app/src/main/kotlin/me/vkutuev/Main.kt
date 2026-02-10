package me.vkutuev

import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.extension.StringExtension.many
import org.ucfs.grammar.combinator.extension.StringExtension.or
import org.ucfs.grammar.combinator.extension.StringExtension.times
import org.ucfs.grammar.combinator.regexp.Nt
import org.ucfs.grammar.combinator.regexp.many
import org.ucfs.input.DotParser
import org.ucfs.input.InputGraph
import org.ucfs.input.TerminalInputLabel
import org.ucfs.parser.Gll
import org.ucfs.sppf.node.*

data class Input(val graphDot: String, val grammar: Grammar)

class RequestGrammar : Grammar() {
    val S by Nt().asStart()
    val R by Nt(many("assign_r" or "load_0_r" or "load_1_r"))

    init {
        S /= many("assign") * many(R * ("store_0" or "store_1")) * "pt"
    }
}

fun getInput(name: String): InputGraph<Int, TerminalInputLabel> {
    val dotGraph = object {}.javaClass.getResource("/$name")?.readText()
        ?: throw RuntimeException("File $name not found in resources")
    val dotParser = DotParser()
    return dotParser.parseDot(dotGraph)
}

data class OutEgde(val start: Int, val symbol: String, val end: Int)

fun getPathFromSppf(node: RangeSppfNode<Int>): List<OutEgde> {
    when (val nodeType = node.type) {
        is TerminalType<*> -> {
            val range = node.inputRange ?: throw RuntimeException("Null inputRange for TerminalType node of SPPF")
            return listOf(OutEgde(range.from, nodeType.terminal.toString(), range.to))
        }

        is NonterminalType if nodeType.startState.nonterminal.name == "R" -> {
            val range = node.inputRange ?: throw RuntimeException("Null inputRange for R Nonterminal node of SPPF")
            return listOf(OutEgde(range.from, "R", range.to))
        }

        is EpsilonNonterminalType -> {
            return emptyList()
        }

        is EmptyType -> {
            throw RuntimeException("SPPF cannot contain EmptyRange")
        }

        else -> {
            return node.children.flatMap { getPathFromSppf(it) }
        }
    }
}

fun main() {
    val graph = getInput("graph_1.dot")
    val grammar = RequestGrammar()
    val gll = Gll.gll(grammar.rsm, graph)
    val sppf = gll.parse()
    println("Result length ${sppf.size}")
    sppf.forEach {
        println(getPathFromSppf(it).toString())
    }
}
