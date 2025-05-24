package org.ucfs.optbench

import org.ucfs.optbench.testsource.*
import java.io.File
import kotlin.random.Random

fun bench(
    what: TestGenerator,
    initial: Int,
    cap: Int,
    tests: Int = 100,
): List<TestResult> {
    var size = initial

    val seed = Random.nextInt()

    return sequence {
        while (size < cap) {
            what
                .generateSource(seed, size, tests)
                .run()
                .also { println(it) }
                .also { yield(it) }
            size = size * 3 / 2
        }
    }.toList()
}

fun warmup() {
    ExpressionTestGenerator().generateSource(100, 1000, 100).run()
}

fun benchDyck() = bench(DyckTestGenerator(), 100, 20000, 50)

fun benchDyck3() = bench(Dyck3TestGenerator(), 100, 20000, 50)

fun benchExpression() = bench(ExpressionTestGenerator(), 100, 20000, 50)

fun benchPalindrome() = bench(PalindromeTestGenerator(), 100, 15000, 50)

fun benchUnequalBlocks() = bench(UnequalBlocksTestGenerator(), 100, 600, 50)

fun benchNonSquare() = bench(NonSquareTestGenerator(), 100, 600, 50)

fun main() {
    warmup()
    (
        benchDyck() +
            benchDyck3() +
            benchExpression() +
            benchPalindrome() +
            benchUnequalBlocks() +
            benchNonSquare()
    ).dumpToCsv(File("cache_hash.csv"))
}
