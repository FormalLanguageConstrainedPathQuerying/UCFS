package org.ucfs.descriptors

import org.ucfs.collections.InternTable
import java.util.LinkedList

/**
 * Collection of default descriptors
 * @param VertexType - type of vertex in input graph
 */
open class DescriptorsStorage<VertexType> {
    /**
     * Descriptors already scheduled or explicitly marked as handled.
     */
    private val handledDescriptors = InternTable<Descriptor<VertexType>>()

    private val descriptorsToHandle = LinkedList<Descriptor<VertexType>>()

    private fun isEmpty() = descriptorsToHandle.isEmpty()


    fun addToHandled(descriptor: Descriptor<VertexType>) {
        handledDescriptors.addIfAbsent(descriptor)
    }

    fun add(descriptor: Descriptor<VertexType>) {
        if (handledDescriptors.addIfAbsent(descriptor)) {
            descriptorsToHandle.addLast(descriptor)
        }
    }

    /**
     * Gets next descriptor to handle
     * @return default descriptor if there is available one, null otherwise
     */
    fun nextToHandle(): Descriptor<VertexType>? {
        if (!isEmpty()) {
            return descriptorsToHandle.removeLast()
        }
        return null
    }
}

