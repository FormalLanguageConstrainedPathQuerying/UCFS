package org.ucfs.rsm.symbol

import org.ucfs.rsm.RsmState
import java.util.*

private object NonterminalIdGenerator {
    private var id = 0

    fun getId() = id++
}

data class Nonterminal(val name: String?) : Symbol {
    lateinit var startState: RsmState
    private var rsmStateLastId = 0
    private val id = NonterminalIdGenerator.getId()

    override fun toString() = "Nonterminal(${name ?: this.hashCode()}@$id)"

    override fun hashCode() = id

    override fun equals(other: Any?) = other != null && other is Nonterminal && other.id == id

    fun getNextRsmStateId(): Int {
        val id = rsmStateLastId
        rsmStateLastId++
        return id
    }

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
            for ((_, destinationState) in state.outgoingEdges) {
                if (!used.contains(destinationState)) {
                    queue.add(destinationState)
                }
            }
        }
        return used
    }
}
