package org.ucfs.optbench.testsource

import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.regexp.Nt
import org.ucfs.grammar.combinator.regexp.or
import org.ucfs.grammar.combinator.regexp.times
import org.ucfs.optbench.*
import org.ucfs.optbench.lineEnd
import org.ucfs.rsm.symbol.Term
import kotlin.random.Random

class ExpressionGrammar : Grammar() {
    val SS by Nt()
    val Statement by Nt()
    val Expression by Nt()
    val Multiplier by Nt()
    val Summand by Nt()
    val Variable by Nt()

    val Assign = Term(":=")
    val Mul = Term("*")
    val Div = Term("/")
    val Plus = Term("+")
    val Minus = Term("-")
    val LBrace = Term("(")
    val RBrace = Term(")")
    val X = Term("x")
    val Y = Term("y")
    val Z = Term("z")

    // grammar intentionally made right-associative not to be left-recursive
    init {
        setStart(SS)
        SS /= Statement * lineEnd
        Variable /= X or Y or Z
        Statement /= Variable * Assign * Expression
        Multiplier /= Variable or LBrace * Expression * RBrace
        Summand /= Multiplier or Multiplier * Mul * Summand or Multiplier * Div * Summand
        Expression /= Summand or Summand * Plus * Expression or Summand * Minus * Expression
    }
}

class ExpressionTestGenerator : TestGenerator {
    override val grammar = ExpressionGrammar()
    override val name = "Expression"
    override val generator =
        TrivialGenerator {
                seed, size ->
            (generateStatement(seed, size) + lineEndToken) with RecognizerOutput.Accept
        }
}

val lineEndToken = lineEndSymbol of 1
private val variables = listOf("x " of 1, "y " of 1, "z " of 1)
private val assign = ":= " of 1
private val mulSigns = listOf("* " of 1, "/ " of 1)
private val plusSigns = listOf("+ " of 1, "- " of 1)
private val lBrace = "( " of 1
private val rBrace = ") " of 1

private fun generateVariable(seed: Int) = variables[Random(seed).nextInt(0, 3)]

private fun generateMultiplier(
    seed: Int,
    size: Int,
): CountedInput {
    if (size == 0) throw Exception("cannot create multiplier of zero size")
    if (size == 1) return generateVariable(seed + 1)
    return lBrace + generateExpression(seed + 1, size) + rBrace
}

private fun generateSummand(
    seed: Int,
    size: Int,
): CountedInput {
    if (size == 0) throw Exception("cannot create summand of zero size")
    if (size == 1) return generateMultiplier(seed, size)
    val rnd = Random(seed)
    val split = rnd.nextInt(0, size)
    val mulSign = mulSigns[rnd.nextInt(2)]
    if (split == 0) return generateMultiplier(seed, size)
    return generateSummand(seed + 1, split) + mulSign + generateMultiplier(seed + 2, size - split)
}

private fun generateExpression(
    seed: Int,
    size: Int,
): CountedInput {
    if (size == 0) throw Exception("cannot create expression of zero size")
    if (size == 1) return generateMultiplier(seed, size)
    val rnd = Random(seed)
    val split = rnd.nextInt(0, size - 1)
    val plusSign = plusSigns[rnd.nextInt(2)]
    if (split == 0) return generateSummand(seed + 1, size)
    return generateExpression(seed + 1, split) + plusSign + generateSummand(seed + 2, size - split)
}

private fun generateStatement(
    seed: Int,
    size: Int,
): CountedInput {
    if (size < 2) throw Exception("cannot create statement of size less than three")
    val variable = generateVariable(seed + 1)
    return variable + assign + generateExpression(seed + 2, size - 1)
}
