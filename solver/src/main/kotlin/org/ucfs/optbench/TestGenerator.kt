package org.ucfs.optbench

import org.ucfs.grammar.combinator.Grammar
import kotlin.random.Random

interface TestGenerator {
    val grammar: Grammar
    val name: String
    val generator: SingleTestGenerator
}

interface SingleTestGenerator {
    fun generateTest(
        seed: Int,
        size: Int,
    ): Test
}

class AcceptRejectUniformGenerator(
    val generateAccept: (Int, Int) -> Test,
    val generateReject: (Int, Int) -> Test,
) : SingleTestGenerator {
    override fun generateTest(
        seed: Int,
        size: Int,
    ): Test = if (Random(seed).nextBoolean()) generateAccept(seed, size) else generateReject(seed, size)
}

class TrivialGenerator(val generate: (Int, Int) -> Test) : SingleTestGenerator {
    override fun generateTest(
        seed: Int,
        size: Int,
    ): Test = generate(seed, size)
}

fun TestGenerator.generateSource(
    seed: Int,
    size: Int,
    number: Int,
): TestSource =
    TestSource(
        grammar,
        Random(seed)
            .let {
                    rnd ->
                sequence {
                    repeat(number) { yield(generator.generateTest(rnd.nextInt(), size)) }
                }.toList()
            },
        name,
        size,
    )
