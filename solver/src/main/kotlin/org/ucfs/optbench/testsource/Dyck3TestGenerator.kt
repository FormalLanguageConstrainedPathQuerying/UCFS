package org.ucfs.optbench.testsource

import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.regexp.*
import org.ucfs.optbench.*
import org.ucfs.rsm.symbol.Term
import kotlin.random.Random

class Dyck3Grammar : Grammar() {
    val SS by Nt()
    val S by Nt()
    val Round by Nt()
    val Square by Nt()
    val Angle by Nt()

    init {
        setStart(SS)
        Round /= Term("(") * S * Term(")") * S
        Square /= Term("[") * S * Term("]") * S
        Angle /= Term("<") * S * Term(">") * S
        S /= Epsilon or Round or Square or Angle
        SS /= S * lineEnd
    }
}

class Dyck3TestGenerator : TestGenerator {
    override val name = "Dyck-3"
    override val grammar = Dyck3Grammar()
    override val generator =
        AcceptRejectUniformGenerator(
            { seed, size -> genDyck3Ok(seed, size) + lineEndToken with RecognizerOutput.Accept },
            { seed, size -> genDyck3Fail(seed, size) + lineEndToken with RecognizerOutput.Reject },
        )
}

private val lBrace = listOf("( " of 1, "[ " of 1, "< " of 1)
private val rBrace = listOf(") " of 1, "] " of 1, "> " of 1)

private fun genDyck3Ok(
    seed: Int,
    size: Int,
): CountedInput {
    val rnd = Random(seed)
    val brace = rnd.nextInt(0, 3)
    if (size == 0) return "" of 0
    val left = rnd.nextInt(0, size)
    val right = size - left - 1
    return lBrace[brace] + genDyck3Ok(seed + left, left) + rBrace[brace] + genDyck3Ok(seed + right, right)
}

private fun genDyck3Fail(
    seed: Int,
    size: Int,
): CountedInput {
    val rnd = Random(seed)
    val brace = rnd.nextInt(0, 3)
    if (size == 0) {
        throw Exception("Dyck-3 cannot fail empty string")
    }
    val left = rnd.nextInt(0, size)
    val right = size - left - 1
    val failLeft = rnd.nextBoolean()
    if (rnd.nextBoolean() && size > 1) { // Fail later
        return if (right == 0 || failLeft && left > 0) {
            lBrace[brace] +
                genDyck3Fail(seed + left, left) +
                rBrace[brace] +
                genDyck3Ok(seed + right, right)
        } else {
            lBrace[brace] +
                genDyck3Ok(seed + left, left) +
                rBrace[brace] +
                genDyck3Fail(seed + right, right)
        }
    }
    // Fail now
    if (rnd.nextBoolean()) { // different brace types, like: (]
        return lBrace[brace] +
            genDyck3Ok(seed + left, left) +
            rBrace[(brace + 1) % 3] +
            genDyck3Ok(seed + right, right)
    }
    // braces look wrong sides, like: ))
    return rBrace[brace] +
        genDyck3Ok(seed + left, left) +
        rBrace[brace] +
        genDyck3Ok(seed + right, right)
}
