package org.ucfs.descriptors

import org.ucfs.parser.ParsingException

/**
 * Collection of default descriptors
 * @param VertexType - type of vertex in input graph
 */
open class DescriptorsStorage<VertexType>{
    /**
     * Collection of already handled descriptors, accessible via descriptor's hashcode
     */
    private val handledDescriptors = ArrayList<Descriptor<VertexType>>()

    private val descriptorsToHandle = ArrayDeque<Descriptor<VertexType>>()

    private fun isEmpty() = descriptorsToHandle.isEmpty()

    private fun addToHandling(descriptor: Descriptor<VertexType>) {
        if (!isAlreadyHandled(descriptor)) {
            descriptorsToHandle.addLast(descriptor)
        }
    }

    /**
     * @return next default descriptor to handle
     */
    open fun next(): Descriptor<VertexType> {
        if (isEmpty()) {
            throw ParsingException("Descriptor storage is empty")
        }
        return descriptorsToHandle.removeLast()
    }

    /**
     * @param descriptor - descriptor to check
     * @return true if the descriptor was already processed, false otherwise
     */
    private fun isAlreadyHandled(descriptor: Descriptor<VertexType>): Boolean {
        val handledDescriptor = handledDescriptors.find { descriptor.hashCode() == it.hashCode() }

        return handledDescriptor != null
    }

    fun addToHandled(descriptor: Descriptor<VertexType>) {
        handledDescriptors.add(descriptor)
    }

    fun add(descriptor: Descriptor<VertexType>) {
        if(!isAlreadyHandled(descriptor)){
            addToHandling(descriptor)
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

