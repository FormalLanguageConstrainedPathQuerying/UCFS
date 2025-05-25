package org.ucfs.optbench.testsource

import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.regexp.*
import org.ucfs.optbench.*
import org.ucfs.rsm.symbol.Term
import kotlin.random.Random

class DyckGrammar : Grammar() {
    val S by Nt()
    val SS by Nt()
    private val lBrace = Term("(")
    private val rBrace = Term(")")

    init {
        setStart(SS)
        S /= Epsilon or lBrace * S * rBrace * S // * many(S)
        SS /= S * lineEnd
    }
}

class DyckAcceptTestGenerator : TestGenerator {
    override val grammar = DyckGrammar()
    override val name = "Dyck-1 Accept"

    override fun generate(
        seed: Int,
        size: Int,
    ) = genDyckOk(seed, size) + lineEndToken with RecognizerOutput.Accept
}

class DyckRejectTestGenerator : TestGenerator {
    override val grammar = DyckGrammar()
    override val name = "Dyck-1 Reject"

    override fun generate(
        seed: Int,
        size: Int,
    ) = genDyckFail(seed, size) + lineEndToken with RecognizerOutput.Reject
}

private val lBrace = "( " of 1
private val rBrace = ") " of 1

private fun genDyckOk(
    seed: Int,
    size: Int,
): CountedInput {
    val rnd = Random(seed)
    if (size == 0) return "" of 0
    val left = rnd.nextInt(0, size)
    val right = size - left - 1
    return lBrace + genDyckOk(seed + left, left) + rBrace + genDyckOk(seed + right, right)
}

private fun genDyckFail(
    seed: Int,
    size: Int,
): CountedInput {
    val rnd = Random(seed)
    if (size == 0) throw Exception("Dyck cannot fail empty string")
    val left = rnd.nextInt(0, size)
    val right = size - left - 1
    if (size == 1 || rnd.nextBoolean()) {
        return rBrace + genDyckOk(seed + left, left) + rBrace + genDyckOk(seed + right, right)
    }

    return if (left == 0 || rnd.nextBoolean() && right > 0) {
        lBrace + genDyckOk(seed + left, left) + rBrace + genDyckFail(seed + right, right)
    } else {
        lBrace + genDyckFail(seed + left, left) + rBrace + genDyckOk(seed + right, right)
    }
}
