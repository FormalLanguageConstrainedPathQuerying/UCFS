package org.ucfs.optbench.testsource

import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.regexp.Nt
import org.ucfs.grammar.combinator.regexp.or
import org.ucfs.grammar.combinator.regexp.times
import org.ucfs.optbench.*
import org.ucfs.rsm.symbol.Term
import kotlin.random.Random

class NonSquareGrammar : Grammar() {
    val SS by Nt()
    val S by Nt()
    val A by Nt()
    val B by Nt()

    var a = Term("a")
    var b = Term("b")

    init {
        setStart(SS)
        SS /= S * lineEnd
        A /= a * A * a or a * A * b or b * A * a or b * A * b or a
        B /= a * B * a or a * B * b or b * B * a or b * B * b or b
        S /= A * B or B * A or A or B
    }
}

class NonSquareAcceptTestGenerator : TestGenerator {
    override val name = "Non Square Accept"
    override val grammar = NonSquareGrammar()

    override fun generate(
        seed: Int,
        size: Int,
    ) = generateNonSquareAccept(seed, size) + lineEndToken with RecognizerOutput.Accept
}

class NonSquareRejectTestGenerator : TestGenerator {
    override val name = "Non Square Reject"
    override val grammar = NonSquareGrammar()

    override fun generate(
        seed: Int,
        size: Int,
    ) = generateNonSquareReject(seed, size) + lineEndToken with RecognizerOutput.Reject
}

private val a = "a " of 1
private val b = "b " of 1

private val letters = listOf(a, b)

private fun generateAny(
    seed: Int,
    size: Int,
): CountedInput {
    var ans = empty
    val rnd = Random(seed)

    repeat(size) { ans += letters[rnd.nextInt(2)] }

    return ans
}

private fun generateNonSquareReject(
    seed: Int,
    size: Int,
): CountedInput = generateAny(seed, size) * 2

private fun generateNonSquareAccept(
    seed: Int,
    size: Int,
): CountedInput {
    val rnd = Random(seed)
    val hack = rnd.nextInt(size)
    val left = generateAny(seed, hack)
    val right = generateAny(seed, size - hack - 1)

    val hackLetters = if (rnd.nextBoolean()) a to b else b to a

    // return hacked string
    if (rnd.nextBoolean()) return left + hackLetters.first + right + left + hackLetters.second + right

    val letter = letters[rnd.nextInt(2)]
    val basic = generateAny(seed, size)
    return basic + letter + basic // return odd length
}
