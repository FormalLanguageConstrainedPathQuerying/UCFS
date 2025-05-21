package org.ucfs.optbench.testsource

import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.regexp.*
import org.ucfs.optbench.*
import org.ucfs.rsm.symbol.Term
import kotlin.random.Random

class UnequalBlocksGrammar : Grammar() {
    val SS by Nt()
    val S by Nt()
    val E by Nt()
    val A by Nt()
    val B by Nt()

    val a = Term("a")
    val b = Term("b")

    init {
        setStart(SS)
        SS /= S * lineEnd
        A /= a or a * A
        B /= b or b * B
        E /= A or B
        S /= E or a * S * b
    }
}

class UnequalBlocksTestGenerator : TestGenerator {
    override val name = "Unequal blocks"
    override val grammar = UnequalBlocksGrammar()
    override val generator =
        AcceptRejectUniformGenerator(
            { seed, size -> generateUBSuccess(seed, size) + lineEndToken with RecognizerOutput.Accept },
            { seed, size -> generateUBFail(seed, size) + lineEndToken with RecognizerOutput.Reject },
        )
}

private val a = "a " of 1
private val b = "b " of 1

private fun generateUBFail(
    seed: Int,
    size: Int,
): CountedInput {
    val rnd = Random(seed)
    // generate equal blocks
    if (size <= 3 || rnd.nextBoolean()) return a * size + b * size
    val smaller = rnd.nextInt(size - 1)
    val hack = rnd.nextInt(1, size - 1)
    // generate something that is not a^nb^m
    return if (rnd.nextBoolean()) {
        a * smaller + b * hack + a + b * (size - hack - 1)
    } else {
        a * hack + b + a * (size - hack - 1) + b * smaller
    }
}

private fun generateUBSuccess(
    seed: Int,
    size: Int,
): CountedInput {
    val rnd = Random(seed)
    if (size == 0) throw Exception("Cannot create unequal blocks of size 0")
    val smaller = rnd.nextInt(size - 1)
    return if (rnd.nextBoolean()) {
        a * smaller + b * size
    } else {
        a * size + b * smaller
    }
}
