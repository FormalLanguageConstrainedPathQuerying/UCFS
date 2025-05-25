package org.ucfs.optbench.testsource

import org.ucfs.optbench.RecognizerOutput
import org.ucfs.optbench.Test
import org.ucfs.optbench.repeat

data class CountedInput(val input: String, val tokens: Int) {
    operator fun plus(other: CountedInput) = CountedInput(input + other.input, tokens + other.tokens)
}

infix fun CountedInput.with(output: RecognizerOutput) = Test(input, tokens, output)

infix fun String.of(tokens: Int) = CountedInput(this, tokens)

val empty = "" of 0

fun CountedInput.repeat(times: Int) = CountedInput(input.repeat(times), tokens * times)

operator fun CountedInput.times(times: Int) = repeat(times)
