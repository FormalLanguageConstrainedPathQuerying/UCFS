package org.ucfs.descriptors

import org.ucfs.parser.ParsingException

/**
 * Collection of default descriptors
 * @param VertexType - type of vertex in input graph
 */
open class DescriptorsStorage<VertexType> {
    /**
     * Collection of already handled descriptors, accessible via descriptor's hashcode
     */
    private val handledDescriptors = HashSet<Descriptor<VertexType>>()

    private val descriptorsToHandle = ArrayDeque<Descriptor<VertexType>>()

    private fun isEmpty() = descriptorsToHandle.isEmpty()

    /**
     * @return next default descriptor to handle
     */
    open fun next(): Descriptor<VertexType> {
        if (isEmpty()) {
            throw ParsingException("Descriptor storage is empty")
        }
        return descriptorsToHandle.removeLast()
    }

    fun addToHandled(descriptor: Descriptor<VertexType>) {
        handledDescriptors.add(descriptor)
    }

    fun add(descriptor: Descriptor<VertexType>) {
        if (!handledDescriptors.contains(descriptor)) {
            descriptorsToHandle.addLast(descriptor)
        }
    }

    /**
     * Gets next descriptor to handle
     * @return default descriptor if there is available one, null otherwise
     */
    fun nextToHandle(): Descriptor<VertexType>? {
        if (!isEmpty()) {
            return next()
        }
        return null
    }
}

