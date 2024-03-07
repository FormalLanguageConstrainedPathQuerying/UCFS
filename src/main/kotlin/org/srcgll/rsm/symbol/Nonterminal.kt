package org.srcgll.rsm.symbol

import org.srcgll.incrementalDfs
import org.srcgll.rsm.RsmState
import java.util.*

class Nonterminal(val name: String?) : Symbol {
    lateinit var startState: RsmState
    override fun toString() = "Nonterminal(${name ?: this.hashCode()})"

    /**
     * Get all states from RSM for current nonterminal
     */
    fun getStates(): Iterable<RsmState> {
        val used = HashSet<RsmState>()
        val queue = LinkedList<RsmState>()
        queue.add(startState)
        while (queue.isNotEmpty()) {
            val state = queue.remove()
            used.add(state)
            for (nextState in state.outgoingEdges.values.flatten()) {
                if (!used.contains(nextState)) {
                    queue.add(nextState)
                }
            }
        }
        return used
    }

    /**
     * Get all terminals used in RSM from current state (recursive)
     */
    fun getTerminals(): HashSet<Terminal<*>> {
        return incrementalDfs(startState,
            { state: RsmState -> state.terminalEdges.values.flatten() },
            hashSetOf(),
            { state, set -> set.addAll(state.terminalEdges.keys) })
    }
}
