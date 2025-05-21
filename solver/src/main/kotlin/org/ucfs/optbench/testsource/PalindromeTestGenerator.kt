package org.ucfs.optbench.testsource

import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.regexp.Epsilon
import org.ucfs.grammar.combinator.regexp.Nt
import org.ucfs.grammar.combinator.regexp.or
import org.ucfs.grammar.combinator.regexp.times
import org.ucfs.optbench.*
import org.ucfs.rsm.symbol.Term
import kotlin.random.Random

class PalindromeGrammar : Grammar() {
    val SS by Nt()
    val S by Nt()

    val a = Term("a")
    val b = Term("b")
    val c = Term("c")

    init {
        setStart(SS)
        SS /= S * lineEnd
        S /= Epsilon or a or b or c or a * S * a or b * S * b or c * S * c
    }
}

class PalindromeTestGenerator : TestGenerator {
    override val grammar = PalindromeGrammar()
    override val name = "Palindrome"
    override val generator =
        AcceptRejectUniformGenerator(
            { seed, size -> generatePalindrome(seed, size) + lineEndToken with RecognizerOutput.Accept },
            { seed, size -> generatePalindromeFail(seed, size) + lineEndToken with RecognizerOutput.Reject },
        )
}

private val letters = listOf("a " of 1, "b " of 1, "c " of 1)

private fun generatePalindrome(
    seed: Int,
    size: Int,
): CountedInput {
    if (size == 0) return empty
    val letter = letters[Random(seed).nextInt(3)]
    if (size == 1) return letter
    return letter + generatePalindrome(seed + 1, size - 2) + letter
}

private fun generatePalindromeFail(
    seed: Int,
    size: Int,
): CountedInput {
    if (size < 2) throw Exception("too short to fail")
    val rnd = Random(seed)
    val letterIndex = rnd.nextInt(3)
    if (size < 4 || rnd.nextInt(10) == 0) { // fail now
        return letters[letterIndex] + generatePalindrome(seed + 1, size - 2) + letters[(letterIndex + 1) % 3]
    }
    // fail later
    return letters[letterIndex] + generatePalindromeFail(seed + 1, size - 2) + letters[letterIndex]
}
