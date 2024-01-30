package org.srcgll.sppf

import org.srcgll.rsm.RsmState
import org.srcgll.rsm.symbol.Nonterminal
import org.srcgll.rsm.symbol.Terminal
import org.srcgll.sppf.node.*

open class Sppf<VertexType> {
    private val createdSppfNodes: HashMap<SppfNode<VertexType>, SppfNode<VertexType>> = HashMap()
    private val createdTerminalNodes: HashMap<VertexType, HashSet<TerminalSppfNode<VertexType>>> = HashMap()
    private val minDistanceRecognisedBySymbol: HashMap<SymbolSppfNode<VertexType>, Int> = HashMap()

    fun minDistance(root: ISppfNode): Int {
        val cycle = HashSet<ISppfNode>()
        val visited = HashSet<ISppfNode>()
        val stack = ArrayDeque(listOf(root))
        var curSPPFNode: ISppfNode
        var minDistance = 0

        while (stack.isNotEmpty()) {
            curSPPFNode = stack.last()
            visited.add(curSPPFNode)

            if (!cycle.contains(curSPPFNode)) {
                cycle.add(curSPPFNode)

                when (curSPPFNode) {
                    is TerminalSppfNode<*> -> {
                        minDistance++
                    }

                    is PackedSppfNode<*> -> {
                        if (curSPPFNode.rightSppfNode != null) stack.add(curSPPFNode.rightSppfNode!!)
                        if (curSPPFNode.leftSppfNode != null) stack.add(curSPPFNode.leftSppfNode!!)
                    }

                    is IntermediateSppfNode<*> -> {
                        if (curSPPFNode.children.isNotEmpty()) {
                            curSPPFNode.children.findLast {
                                it.rightSppfNode != curSPPFNode && it.leftSppfNode != curSPPFNode && !visited.contains(
                                    it
                                )
                            }?.let { stack.add(it) }
                            curSPPFNode.children.forEach { visited.add(it) }
                        }
                    }

                    is SymbolSppfNode<*> -> {
                        if (minDistanceRecognisedBySymbol.containsKey(curSPPFNode)) {
                            minDistance += minDistanceRecognisedBySymbol[curSPPFNode]!!
                        } else {
                            if (curSPPFNode.children.isNotEmpty()) {
                                curSPPFNode.children.findLast {
                                    it.rightSppfNode != curSPPFNode && it.leftSppfNode != curSPPFNode && !visited.contains(
                                        it
                                    )
                                }?.let { stack.add(it) }
                                curSPPFNode.children.forEach { visited.add(it) }
                            }
                        }
                    }
                }
            }
            if (curSPPFNode == stack.last()) {
                stack.removeLast()
                cycle.remove(curSPPFNode)
            }
        }

        minDistanceRecognisedBySymbol[root as SymbolSppfNode<VertexType>] = minDistance

        return minDistance
    }

    fun removeNode(sppfNode: SppfNode<VertexType>) {
        createdSppfNodes.remove(sppfNode)
        if (sppfNode is TerminalSppfNode<*>) {
            createdTerminalNodes.remove(sppfNode.leftExtent)
        }
    }

    /**
     * Receives two subtrees of SPPF and connects them via PackedNode.
     *
     * Get or create ParentNode. If given subtrees parse Nonterminal and state is final, then getOrCreate SymbolNode.
     * Otherwise, getOrCreate IntermediateNode.
     *
     * PackedNode then attached to ParentNode as a child
     *
     * @return ParentNode
     */
    fun getParentNode(
        state: RsmState,
        sppfNode: SppfNode<VertexType>?,
        nextSppfNode: SppfNode<VertexType>,
    ): SppfNode<VertexType> {
        val leftExtent = sppfNode?.leftExtent ?: nextSppfNode.leftExtent
        val rightExtent = nextSppfNode.rightExtent

        val packedNode = PackedSppfNode(nextSppfNode.leftExtent, state, sppfNode, nextSppfNode)

        val parent: ParentSppfNode<VertexType> =
            if (state.isFinal) getOrCreateSymbolSppfNode(state.nonterminal, leftExtent, rightExtent, packedNode.weight)
            else getOrCreateIntermediateSppfNode(state, leftExtent, rightExtent, packedNode.weight)

        //  Restrict SPPF from creating loops PARENT -> PACKED -> PARENT
        if (sppfNode != null || parent != nextSppfNode) {
            sppfNode?.parents?.add(packedNode)
            nextSppfNode.parents.add(packedNode)
            packedNode.parents.add(parent)

            parent.children.add(packedNode)
        }

        updateWeights(parent)


        return parent
    }

    fun getOrCreateTerminalSppfNode(
        terminal: Terminal<*>?,
        leftExtent: VertexType,
        rightExtent: VertexType,
        weight: Int = 0,
    ): SppfNode<VertexType> {
        val node = TerminalSppfNode(terminal, leftExtent, rightExtent, weight)

        if (!createdSppfNodes.containsKey(node)) {
            createdSppfNodes[node] = node
        }
        if (!createdTerminalNodes.containsKey(leftExtent)) {
            createdTerminalNodes[leftExtent] = HashSet()
        }
        createdTerminalNodes[leftExtent]!!.add(createdSppfNodes[node] as TerminalSppfNode<VertexType>)

        return createdSppfNodes[node]!!
    }

    fun getOrCreateIntermediateSppfNode(
        state: RsmState,
        leftExtent: VertexType,
        rightExtent: VertexType,
        weight: Int = Int.MAX_VALUE,
    ): ParentSppfNode<VertexType> {
        val node = IntermediateSppfNode(state, leftExtent, rightExtent)
        node.weight = weight

        if (!createdSppfNodes.containsKey(node)) {
            createdSppfNodes[node] = node
        }

        return createdSppfNodes[node]!! as IntermediateSppfNode
    }

    fun getOrCreateSymbolSppfNode(
        nonterminal: Nonterminal,
        leftExtent: VertexType,
        rightExtent: VertexType,
        weight: Int = Int.MAX_VALUE,
    ): SymbolSppfNode<VertexType> {
        val node = SymbolSppfNode(nonterminal, leftExtent, rightExtent)
        node.weight = weight

        if (!createdSppfNodes.containsKey(node)) {
            createdSppfNodes[node] = node
        }

        return createdSppfNodes[node]!! as SymbolSppfNode
    }

    fun invalidate(vertex: VertexType, parseResult: ISppfNode) {
        val queue = ArrayDeque<ISppfNode>()
        val added = HashSet<ISppfNode>()
        var curSPPFNode: ISppfNode? = parseResult

        createdTerminalNodes[vertex]!!.forEach { node ->
            queue.add(node)
            added.add(node)
        }

        while (queue.isNotEmpty()) {
            curSPPFNode = queue.removeFirst()

            when (curSPPFNode) {
                is ParentSppfNode<*> -> {
                    if (curSPPFNode.children.isEmpty()) {
                        curSPPFNode.parents.forEach { packed ->
                            if (!added.contains(packed)) {
                                queue.addLast(packed)
                                added.add(packed)
                            }
                            (packed as PackedSppfNode<VertexType>).rightSppfNode = null
                            (packed as PackedSppfNode<VertexType>).leftSppfNode = null
                        }
                        removeNode(curSPPFNode as SppfNode<VertexType>)
                    }
                }

                is PackedSppfNode<*> -> {
                    curSPPFNode.parents.forEach { parent ->
                        if ((parent as ParentSppfNode<*>).children.contains(curSPPFNode)) {
                            if (!added.contains(parent)) {
                                queue.addLast(parent)
                                added.add(parent)
                            }
                            parent.children.remove(curSPPFNode)
                        }
                    }
                }

                is TerminalSppfNode<*> -> {
                    curSPPFNode.parents.forEach { packed ->
                        if (!added.contains(packed)) {
                            queue.addLast(packed)
                            added.add(packed)
                        }
                        (packed as PackedSppfNode<VertexType>).rightSppfNode = null
                        (packed as PackedSppfNode<VertexType>).leftSppfNode = null
                    }
                    removeNode(curSPPFNode as SppfNode<VertexType>)
                }
            }

            if (curSPPFNode != parseResult) {
                curSPPFNode.parents.clear()
            }
        }
    }

    fun updateWeights(sppfNode: ISppfNode) {
        val added = HashSet<ISppfNode>(listOf(sppfNode))
        val queue = ArrayDeque(listOf(sppfNode))
        var curSPPFNode: ISppfNode

        while (queue.isNotEmpty()) {
            curSPPFNode = queue.removeFirst()

            when (curSPPFNode) {
                is ParentSppfNode<*> -> {
                    val oldWeight = curSPPFNode.weight
                    var newWeight = Int.MAX_VALUE

                    curSPPFNode.children.forEach { newWeight = minOf(newWeight, it.weight) }

                    if (oldWeight > newWeight) {
                        curSPPFNode.weight = newWeight

                        curSPPFNode.children.forEach { if (it.weight > newWeight) it.parents.remove(curSPPFNode) }
                        curSPPFNode.children.removeIf { it.weight > newWeight }

                        curSPPFNode.parents.forEach {
                            queue.addLast(it)
                            added.add(it)
                        }
                    }
                }

                is PackedSppfNode<*> -> {
                    val oldWeight = curSPPFNode.weight
                    val newWeight = (curSPPFNode.leftSppfNode?.weight ?: 0) + (curSPPFNode.rightSppfNode?.weight ?: 0)

                    if (oldWeight > newWeight) {
                        curSPPFNode.weight = newWeight

                        curSPPFNode.parents.forEach {
                            queue.addLast(it)
                            added.add(it)
                        }
                    }
                }

                else -> {
                    throw Error("Terminal node can not be parent")
                }
            }
        }
    }
}