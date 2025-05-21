package org.ucfs.optbench

import org.ucfs.rsm.symbol.Term
import java.lang.String.format
import kotlin.collections.HashSet

const val lineEndSymbol = "$"
val lineEnd = Term(lineEndSymbol)

fun String.repeat(n: Int) =
    generateSequence { this }
        .take(n)
        .fold("") { acc, s -> acc + s }

fun String.chars(n: Int) = if (length < n) " ".repeat(n - length) + this else this

fun Number.chars(n: Int) = format("%${n}d", this)

fun <T> parserOutputSame(
    left: ParserOutput<T>,
    right: ParserOutput<T>,
): Boolean {
    val visitedLeft = HashSet<Int>()
    val visitedRight = HashSet<Int>()

    fun checkSame(
        left: ParserOutput<T>,
        right: ParserOutput<T>,
    ): Boolean {
        if (left == null && right == null) return true
        if (left == null || right == null) return false

        if (visitedLeft.contains(left.id) && visitedRight.contains(right.id)) return true
        if (visitedLeft.contains(left.id) || visitedRight.contains(right.id)) return false

        visitedLeft.add(left.id)
        visitedRight.add(right.id)

        if (left.type != right.type) return false
        if (left.rsmRange != right.rsmRange) return false
        if (left.inputRange != right.inputRange) return false
        if (left.children.size != right.children.size) return false

        left.children.forEachIndexed { index, it -> if (!checkSame(it, right.children[index])) return false }

        return true
    }

    return checkSame(left, right)
}

infix fun <T> ParserOutput<T>.differsFrom(other: ParserOutput<T>) = !parserOutputSame(this, other)
