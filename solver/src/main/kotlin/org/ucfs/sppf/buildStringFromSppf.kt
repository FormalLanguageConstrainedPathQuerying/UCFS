package org.ucfs.sppf

import org.ucfs.sppf.node.*

/**
 * Collects leaves of the derivation tree in order from left to right.
 * @return Ordered collection of terminals
 */
fun <InputNode>buildTokenStreamFromSppf(sppfNode: RangeSppfNode<InputNode>): MutableList<String> {
    val visited: HashSet<RangeSppfNode<InputNode>> = HashSet()
    val stack: ArrayDeque<RangeSppfNode<InputNode>> = ArrayDeque(listOf(sppfNode))
    val result: MutableList<String> = ArrayList()
    var curNode: RangeSppfNode<InputNode>

    while (stack.isNotEmpty()) {
        curNode = stack.removeLast()
        visited.add(curNode)

        val type = curNode.type
        when (type) {
            is TerminalType<*> -> {
                result.add(type.terminal.toString())
            }

            is IntermediateType<*> -> {
                for(child in curNode.children) {
                    stack.add(child)
                }
            }

            is NonterminalType -> {
                if (curNode.children.isNotEmpty()) {
                    curNode.children.findLast {
                        !visited.contains(
                            it
                        )
                    }?.let { stack.add(it) }
                    curNode.children.forEach { visited.add(it) }
                }
            }
        }

    }
    return result
}

/**
 * Collects leaves of the derivation tree in order from left to right and joins them into one string
 * @return String value of recognized subrange
 */
fun <InputNode> buildStringFromSppf(sppfNode: RangeSppfNode<InputNode>): String {
    return buildTokenStreamFromSppf(sppfNode).joinToString(separator = "")
}