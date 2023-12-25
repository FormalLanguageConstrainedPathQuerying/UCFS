package org.srcgll.rsm

import org.srcgll.rsm.symbol.Nonterminal
import org.srcgll.rsm.symbol.Symbol
import org.srcgll.rsm.symbol.Terminal
import java.io.File


private fun getAllStates(startState: RSMState): HashSet<RSMState> {
    val states: HashSet<RSMState> = HashSet()
    val queue = ArrayDeque(listOf(startState))
    while (!queue.isEmpty()) {
        val state = queue.removeFirst()
        if (!states.contains(state)) {
            states.add(state)

            for ((symbol, destStates) in state.outgoingEdges) {
                if (symbol is Nonterminal) {
                    queue.addLast(symbol.startState)
                }
                for (destState in destStates) {
                    queue.addLast(destState)
                    queue.addLast(destState.nonterminal.startState)
                }
            }
        }
    }
    return states
}

fun writeRSMToTXT(startState: RSMState, pathToTXT: String) {
    var lastId = 0
    val stateToId: HashMap<RSMState, Int> = HashMap()

    fun getId(state: RSMState): Int {
        return stateToId.getOrPut(state) { lastId++ }
    }

    val states = getAllStates(startState)
    File(pathToTXT).printWriter().use { out ->
        out.println(
            """StartState(
            |id=${getId(startState)},
            |nonterminal=Nonterminal("${startState.nonterminal.name}"),
            |isStart=${startState.isStart},
            |isFinal=${startState.isFinal}
            |)"""
                .trimMargin()
                .replace("\n", "")
        )

        states.forEach { state ->
            out.println(
                """State(
                |id=${getId(state)},
                |nonterminal=Nonterminal("${state.nonterminal.name}"),
                |isStart=${state.isStart},
                |isFinal=${state.isFinal}
                |)"""
                    .trimMargin()
                    .replace("\n", "")
            )
        }

        fun getSymbolView(symbol: Symbol): Triple<String, String, String> {
            return when (symbol) {
                is Terminal<*> -> Triple("Terminal", symbol.value.toString(), "terminal")
                is Nonterminal -> Triple("Nonterminal", symbol.name?: "NON_TERM", "nonterminal")
                else -> throw Exception("Unsupported implementation of Symbol instance: ${symbol.javaClass}")
            }
        }

        for (state in states) {
            for ((symbol, destStates) in state.outgoingEdges) {
                val (typeView, symbolView, typeLabel) = getSymbolView(symbol)
                for (destState in destStates) {
                    out.println(
                        """${typeView}Edge(
                        |tail=${getId(state)},
                        |head=${getId(destState)},
                        |$typeLabel=$typeView("$symbolView")
                        |)""".trimMargin().replace("\n", "")
                    )
                }
            }
        }
    }

}


fun writeRSMToDOT(startState: RSMState, pathToTXT: String) {
    var lastId = 0
    val stateToId: HashMap<RSMState, Int> = HashMap()

    fun getId(state: RSMState): Int {
        return stateToId.getOrPut(state) { lastId++ }
    }

    val states = getAllStates(startState)
    val boxes: HashMap<Nonterminal, HashSet<RSMState>> = HashMap()

    for (state in states) {
        if (!boxes.containsKey(state.nonterminal)) {
            boxes[state.nonterminal] = HashSet()
        }
        boxes.getValue(state.nonterminal).add(state)
    }

    File(pathToTXT).printWriter().use { out ->
        out.println("digraph g {")

        states.forEach { state ->
<<<<<<< HEAD
            if (state.isStart && state.isFinal) {
                out.println("${state.id} [label = \"${state.nonterminal.name},${state.id}\", shape = doublecircle, color = green]")
            } else if (state.isStart) {
                out.println("${state.id} [label = \"${state.nonterminal.name},${state.id}\", shape = circle, color = green]")
            } else if (state.isFinal) {
                out.println("${state.id} [label = \"${state.nonterminal.name},${state.id}\", shape = doublecircle, color = red]")
            } else {
                out.println("${state.id} [label = \"${state.nonterminal.name},${state.id}\", shape = circle]")
            }
=======
            if (state.isStart)
                out.println("${getId(state)} [label = \"${state.nonterminal.name},${getId(state)}\", shape = circle, color = green]")
            else if (state.isFinal)
                out.println("${getId(state)} [label = \"${state.nonterminal.name},${getId(state)}\", shape = doublecircle, color = red]")
            else
                out.println("${getId(state)} [label = \"${state.nonterminal.name},${getId(state)}\", shape = circle]")
>>>>>>> c4ef2d5792f056dd567cdd251f39b9f0c61fabd3
        }

        fun getView(symbol: Symbol) {
            when (symbol) {
                is Nonterminal -> symbol.name
                is Terminal<*> -> symbol.value
                else -> symbol.toString()
            }
        }
        states.forEach { state ->
            state.outgoingEdges.forEach { (symbol, destStates) ->
                destStates.forEach { destState ->
                    out.println("${getId(state)} -> ${getId(destState)} [label = \"${getView(symbol)}\"]")
                }
            }
        }

        boxes.forEach { box ->
            out.println("subgraph cluster_${box.key.name} {")

            box.value.forEach { state ->
                out.println("${getId(state)}")
            }
            out.println("label = \"${box.key.name}\"")
            out.println("}")
        }
        out.println("}")
    }
}
