package org.ucfs.optbench

import org.ucfs.grammar.combinator.Grammar
import kotlin.random.Random

interface TestGenerator {
    val name: String
    val grammar: Grammar

    fun generate(
        seed: Int,
        size: Int,
    ): Test
}

fun TestGenerator.generateSource(
    seed: Int,
    size: Int,
    number: Int,
): TestSource =
    TestSource(
        grammar,
        Random(seed).let { rnd -> sequence { repeat(number) { yield(generate(rnd.nextInt(), size)) } }.toList() },
        name,
        size,
    )
