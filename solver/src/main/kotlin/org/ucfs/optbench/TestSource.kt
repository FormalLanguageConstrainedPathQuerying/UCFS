package org.ucfs.optbench

import org.ucfs.grammar.combinator.Grammar
import org.ucfs.input.IInputGraph
import org.ucfs.input.LinearInput
import org.ucfs.input.TerminalInputLabel
import org.ucfs.parser.Gll
import org.ucfs.sppf.node.RangeSppfNode
import java.io.File
import kotlin.system.measureNanoTime

data class Test(val input: String, val size: Int, val output: RecognizerOutput)

typealias ParserOutput<T> = RangeSppfNode<T>?

fun <T> ParserOutput<T>.checkRecognize(input: IInputGraph<T, TerminalInputLabel>): RecognizerOutput =
    if (this == null || inputRange == null) {
        RecognizerOutput.Reject
    } else {
        (input.isFinal(inputRange.to) && input.isStart(inputRange.from)).toRecognizerOutput()
    }

fun runGll(
    input: IInputGraph<Int, TerminalInputLabel>,
    grammar: Grammar,
) = Gll.gll(grammar.rsm, input).parse()

fun runTest(
    test: Test,
    grammar: Grammar,
): Triple<Long, ParserOutput<Int>, RecognizerOutput> {
    var result: ParserOutput<Int>
    val input = LinearInput.buildFromString(test.input)
    val time = measureNanoTime { result = runGll(input, grammar) }
    return Triple(time, result, result.checkRecognize(input))
}

enum class RecognizerOutput { Accept, Reject }

fun Boolean.toRecognizerOutput() = if (this) RecognizerOutput.Accept else RecognizerOutput.Reject

data class SingleTest(
    val grammar: String,
    val input: String,
    val expected: RecognizerOutput,
    val actual: RecognizerOutput,
)

data class TestResult(
    val name: String,
    val tests: Int,
    val size: Int,
    val totalRuntime: Long,
    val misses: List<SingleTest>,
) {
    val averageRuntime = totalRuntime / tests

    override fun toString(): String {
        val missesString = if (isOk()) "" else " | misses: ${misses.size}"
        return "name: ${name.chars(20)} | " +
            "size: ${size.chars(6)} | " +
            "tests: ${tests.chars(4)} | " +
            "initial: ${averageRuntime.chars(15)}ns | " +
            missesString
    }
}

fun TestResult.isOk() = misses.isEmpty()

data class TestSource(val grammar: Grammar, val inputs: Collection<Test>, val name: String, val size: Int) {
    fun run(): TestResult {
        var totalRuntime: Long = 0
        val misses = mutableListOf<SingleTest>()
        inputs.forEach {
            val actual = runTest(it, grammar)
            totalRuntime += actual.first
            val test = SingleTest(name, it.input, it.output, actual.third)
            if (actual.third != it.output) misses.add(test)
        }
        return TestResult(
            name,
            inputs.size,
            size,
            totalRuntime,
            misses,
        )
    }
}

fun List<TestResult>.dumpToCsv(file: File) {
    val bw = file.bufferedWriter()
    bw.write("name,size,runtime\r\n")

    forEach {
        bw.write(it.name)
        bw.write(",")
        bw.write(it.size.toString())
        bw.write(",")
        bw.write(it.averageRuntime.toString())
        bw.write("\r\n")
    }

    bw.close()
}
