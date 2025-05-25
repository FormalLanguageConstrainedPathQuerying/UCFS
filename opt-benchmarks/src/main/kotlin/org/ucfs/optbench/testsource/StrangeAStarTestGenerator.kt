package org.ucfs.optbench.testsource

import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.regexp.*
import org.ucfs.optbench.*
import org.ucfs.rsm.symbol.Term
import kotlin.random.Random
import kotlin.text.repeat

class StrangeAStar : Grammar() {
    val SS by Nt()
    val S by Nt()

    init {
        setStart(SS)
        S /= Epsilon or Term("a") * S * S * S * S * S * S * S * S * S * many(S)
        SS /= S * lineEnd
    }
}

class StrangeAStarAcceptTestGenerator : TestGenerator {
    override val grammar = StrangeAStar()
    override val name = "Strange A* Accept"

    override fun generate(
        seed: Int,
        size: Int,
    ) = Test(genAStarAccept(seed, size) + lineEndSymbol, size + 1, RecognizerOutput.Accept)
}

class StrangeAStarRejectTestGenerator : TestGenerator {
    override val grammar = StrangeAStar()
    override val name = "Strange A* Reject"

    override fun generate(
        seed: Int,
        size: Int,
    ) = Test(genAStartReject(seed, size) + lineEndSymbol, size + 1, RecognizerOutput.Reject)
}

fun genAStarAccept(
    seed: Int,
    size: Int,
): String {
    return "a ".repeat(size)
}

fun genAStartReject(
    seed: Int,
    size: Int,
): String {
    val fail = Random(seed).nextInt(0, size)
    return "a ".repeat(fail) + "b " + "a ".repeat(size - fail - 1)
}
