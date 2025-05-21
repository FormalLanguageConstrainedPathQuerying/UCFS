package org.ucfs.optbench

import org.ucfs.optbench.testsource.*
import java.io.File

fun main() {
    val generators =
        listOf(
            // StrangeAStarTestGenerator(),
            Dyck3TestGenerator(),
            DyckTestGenerator(),
            ExpressionTestGenerator(),
            PalindromeTestGenerator(),
            UnequalBlocksTestGenerator(),
            NonSquareTestGenerator(),
        )

    sequence {
        generators.forEach {
            var size = 10
            val tests = 100

            while (true) {
                val src = it.generateSource(228, size, tests)
                val result = src.run()
                yield(result)
                println(result)
                if (result.totalRuntime > 10000) break
                size = size * 3 / 2
            }
        }
    }.toList().dumpToCsv(File("unoptimized.csv"))
}
