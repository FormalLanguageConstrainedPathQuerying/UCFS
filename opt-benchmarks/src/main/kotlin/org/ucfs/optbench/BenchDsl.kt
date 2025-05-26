package org.ucfs.optbench

import org.ucfs.optbench.testsource.ExpressionAcceptTestGenerator
import java.io.File
import kotlin.random.Random

class BenchmarkScope {
    val testResults = mutableListOf<TestResult>()

    fun dump(file: File) = testResults.dumpToCsv(file)
}

fun benchmark(run: BenchmarkScope.() -> Unit): BenchmarkScope {
    val bs = BenchmarkScope()
    bs.run()
    return bs
}

fun BenchmarkScope.bench(
    generator: TestGenerator,
    config: TestConfiguration,
) {
    val seed = Random.nextInt()

    testResults +=
        sequence {
            config.runs.forEach { run ->
                generator.generateSource(seed, run.first, run.second).run().also { println(it) }.also { yield(it) }
            }
        }.toList()
}

fun BenchmarkScope.benchMany(
    generators: List<TestGenerator>,
    config: TestConfiguration,
) = generators.forEach { bench(it, config) }

fun BenchmarkScope.warmup() = ExpressionAcceptTestGenerator().generateSource(100, 1000, 100).run()
