package org.srcgll.sppf

import org.srcgll.grammar.TokenSequence
import java.util.*

open class ParentSPPFNode
(
    leftExtent  : TokenSequence,
    rightExtent : TokenSequence,
)
    : SPPFNode(leftExtent, rightExtent, Int.MAX_VALUE)
{
    val kids : HashSet<PackedSPPFNode> = HashSet()
    

    override fun toString() = "ParentSPPFNode(leftExtent=$leftExtent, rightExtent=$rightExtent)"
    
    override fun equals(other : Any?) : Boolean
    {
        if (this === other)           return true

        if (other !is ParentSPPFNode) return false

        if (!super.equals(other))     return false

        return true
    }

    override val hashCode : Int = Objects.hash(leftExtent, rightExtent)
    override fun hashCode() = hashCode
}
