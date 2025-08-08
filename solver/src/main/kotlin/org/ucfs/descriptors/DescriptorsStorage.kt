package org.ucfs.descriptors

import org.ucfs.parser.ParsingException
import java.util.LinkedList

/**
 * Collection of default descriptors
 * @param VertexType - type of vertex in input graph
 */
open class DescriptorsStorage<VertexType> {
    /**
     * Collection of already handled descriptors, accessible via descriptor's hashcode
     */
    private val handledDescriptors = HashSet<Descriptor<VertexType>>()

    private val descriptorsToHandle = LinkedList<Descriptor<VertexType>>()

    private fun isEmpty() = descriptorsToHandle.isEmpty()


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
            return descriptorsToHandle.removeLast()
        }
        return null
    }
}

