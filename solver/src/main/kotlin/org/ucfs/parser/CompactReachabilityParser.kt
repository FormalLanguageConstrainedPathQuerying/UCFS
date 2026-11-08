package org.ucfs.parser

import org.ucfs.collections.InternTable
import org.ucfs.input.ILabel
import org.ucfs.parser.context.Context
import org.ucfs.rsm.RsmState
import org.ucfs.rsm.symbol.ITerminal
import org.ucfs.rsm.symbol.Nonterminal
import org.ucfs.sppf.node.RangeSppfNode
import org.ucfs.sppf.node.ReachabilityRangeSppfNode
import java.util.ArrayDeque

/** A reachability descriptor is identified by its GSS call, position, state, and empty-prefix bit. */
internal class CompactDescriptor<V>(
    val inputPosition: V,
    val gssNode: CompactGssNode<V>,
    val rsmState: RsmState,
    val emptyPrefix: Boolean
) {
    private val cachedHash = (((inputPosition?.hashCode() ?: 0) * 31 +
        System.identityHashCode(gssNode)) * 31 + rsmState.hashCode()) * 31 + emptyPrefix.hashCode()

    override fun hashCode(): Int = cachedHash

    override fun equals(other: Any?): Boolean = this === other ||
        (other is CompactDescriptor<*> && inputPosition == other.inputPosition &&
            gssNode === other.gssNode && rsmState == other.rsmState &&
            emptyPrefix == other.emptyPrefix)
}

/** Canonical GSS call entry; popped end vertices retain no SPPF node. */
internal data class CompactGssNode<V>(val rsm: RsmState, val inputPosition: V) {
    val popped = ArrayList<V>()
    val outgoingEdges = ArrayList<CompactGssEdge<V>>()
    var poppedByTerminal: HashMap<ITerminal, ArrayList<V>>? = null
}

internal class CompactGssEdge<V>(val caller: CompactGssNode<V>, val returnState: RsmState)

/** Mirrors reach-only GLL transitions while retaining only descriptor/GSS state and final pairs. */
internal class CompactReachabilityParser<V, L : ILabel>(
    private val context: Context<V, L>,
    private val indexPoppedByTerminal: Boolean
) {
    private val input = context.input
    private val gssNodes = HashMap<CompactGssNode<V>, CompactGssNode<V>>()
    private val handled = InternTable<CompactDescriptor<V>>(hashGuard = true)
    private val queue = ArrayDeque<CompactDescriptor<V>>()
    private val results = HashSet<Pair<V, V>>()
    private val stats = CompactReachabilityStats.ifEnabled()

    fun parse(): Set<RangeSppfNode<V>> {
        context.resetContinuationCache()
        for (start in input.getInputStartVertices()) {
            val gss = getOrCreateGss(start, context.fictiveStartState)
            enqueue(CompactDescriptor(start, gss, context.fictiveStartState, emptyPrefix = true))
        }
        while (queue.isNotEmpty()) {
            val descriptor = queue.removeLast()
            if (descriptor.rsmState.isFinal) pop(descriptor)
            handleEdges(descriptor)
        }
        return CompactResultView(results, context.fictiveStartState, context.fictiveFinalState)
    }

    private fun getOrCreateGss(position: V, state: RsmState): CompactGssNode<V> {
        val node = CompactGssNode(state, position)
        return gssNodes.getOrPut(node) { node }
    }

    private fun enqueue(descriptor: CompactDescriptor<V>) {
        if (handled.addIfAbsent(descriptor)) {
            stats?.descriptor()
            queue.addLast(descriptor)
        }
    }

    private fun reserveSyntheticEpsilon(descriptor: CompactDescriptor<V>) {
        val synthetic = CompactDescriptor(
            descriptor.inputPosition, descriptor.gssNode, descriptor.rsmState, emptyPrefix = false
        )
        if (handled.addIfAbsent(synthetic)) stats?.descriptor()
    }

    private fun pop(descriptor: CompactDescriptor<V>) {
        if (descriptor.emptyPrefix) reserveSyntheticEpsilon(descriptor)
        val node = descriptor.gssNode
        val end = descriptor.inputPosition
        node.popped.add(end)
        stats?.pop()
        if (indexPoppedByTerminal) indexPopped(node, end)
        for (edge in node.outgoingEdges) {
            if (context.canContinueAt(edge.returnState, end)) {
                enqueue(CompactDescriptor(end, edge.caller, edge.returnState, emptyPrefix = false))
            }
        }
        if (node.rsm == context.fictiveStartState &&
            descriptor.rsmState == context.fictiveFinalState &&
            node.inputPosition in input.getInputStartVertices()) {
            if (results.add(node.inputPosition to end)) stats?.result()
        }
    }

    private fun handleEdges(descriptor: CompactDescriptor<V>) {
        for (edge in input.getEdges(descriptor.inputPosition)) {
            val terminal = edge.label.terminal
            val rsmEdge = terminal?.let { descriptor.rsmState.getTerminalEdge(it) }
            if (rsmEdge != null) {
                enqueue(CompactDescriptor(edge.targetVertex, descriptor.gssNode,
                    rsmEdge.destinationState, emptyPrefix = false))
            }
        }
        for (edge in descriptor.rsmState.nonterminalEdgesStorage) {
            val calleeStart = (edge.symbol as Nonterminal).startState
            val callee = getOrCreateGss(descriptor.inputPosition, calleeStart)
            callee.outgoingEdges.add(CompactGssEdge(descriptor.gssNode, edge.destinationState))
            stats?.edge()
            enqueue(CompactDescriptor(descriptor.inputPosition, callee, calleeStart, emptyPrefix = true))
            for (end in poppedForContinuation(callee, edge.destinationState)) {
                if (context.canContinueAt(edge.destinationState, end)) {
                    enqueue(CompactDescriptor(end, descriptor.gssNode,
                        edge.destinationState, emptyPrefix = false))
                }
            }
        }
    }

    private fun indexPopped(node: CompactGssNode<V>, end: V) {
        val buckets = node.poppedByTerminal ?: HashMap<ITerminal, ArrayList<V>>().also {
            node.poppedByTerminal = it
        }
        val seen = HashSet<ITerminal>()
        for (edge in input.getEdges(end)) {
            val terminal = edge.label.terminal ?: continue
            if (seen.add(terminal)) buckets.getOrPut(terminal) { ArrayList() }.add(end)
        }
    }

    private fun poppedForContinuation(node: CompactGssNode<V>, state: RsmState): Collection<V> {
        if (!indexPoppedByTerminal || state.isFinal || state.nonterminalEdgesStorage.isNotEmpty()) return node.popped
        val edges = state.terminalEdgesStorage
        if (edges.isEmpty()) return emptyList()
        val buckets = node.poppedByTerminal ?: return emptyList()
        if (edges.size == 1) return buckets[edges[0].symbol as ITerminal] ?: emptyList()
        val selected = LinkedHashSet<V>()
        for (edge in edges) selected.addAll(buckets[edge.symbol as ITerminal] ?: continue)
        return selected
    }
}

/** The integration sees final range nodes, created only while iterating the completed result set. */
private class CompactResultView<V>(
    private val pairs: Set<Pair<V, V>>,
    private val start: RsmState,
    private val final: RsmState
) : AbstractSet<RangeSppfNode<V>>() {
    override val size: Int get() = pairs.size

    override fun iterator(): Iterator<RangeSppfNode<V>> {
        val source = pairs.iterator()
        return object : Iterator<RangeSppfNode<V>> {
            override fun hasNext(): Boolean = source.hasNext()
            override fun next(): RangeSppfNode<V> {
                val (from, to) = source.next()
                return ReachabilityRangeSppfNode(from, to, start, final)
            }
        }
    }

    override fun contains(element: RangeSppfNode<V>): Boolean =
        element is ReachabilityRangeSppfNode<*> &&
            element.rsmRange?.from == start && element.rsmRange?.to == final &&
            element.inputRange?.let { (it.from to it.to) in pairs } == true
}

/** Optional coherent counters for timeout diagnostics; disabled in timing controls. */
private class CompactReachabilityStats {
    private var descriptors = 0L
    private var edges = 0L
    private var pops = 0L
    private var results = 0L

    @Synchronized fun descriptor() { descriptors++ }
    @Synchronized fun edge() { edges++ }
    @Synchronized fun pop() { pops++ }
    @Synchronized fun result() { results++ }

    @Synchronized
    private fun snapshot() = longArrayOf(descriptors, edges, pops, results)

    companion object {
        fun ifEnabled(): CompactReachabilityStats? {
            if (!java.lang.Boolean.getBoolean("ucfs.compactReachabilityStats")) return null
            val stats = CompactReachabilityStats()
            Runtime.getRuntime().addShutdownHook(Thread({
                val counts = stats.snapshot()
                System.err.println("COMPACT_REACH stage=final descriptors=${counts[0]} " +
                    "gssEdges=${counts[1]} pops=${counts[2]} results=${counts[3]}")
            }, "ucfs-compact-reach-stats"))
            return stats
        }
    }
}
