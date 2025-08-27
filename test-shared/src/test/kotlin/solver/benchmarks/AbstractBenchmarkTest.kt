package solver.benchmarks

import org.jetbrains.kotlin.incremental.createDirectory
import org.junit.jupiter.api.Test
import org.ucfs.grammar.combinator.Grammar
import org.ucfs.input.DotParser
import org.ucfs.parser.Gll
import org.ucfs.rsm.writeRsmToDot
import org.ucfs.sppf.getSppfDot
import java.io.File
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertFalse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.Path

abstract class AbstractBenchmarkTest {
    val rootPath: Path = Path.of("src", "test", "resources")

    fun getDataFolder(): Path {
        return rootPath.resolve("benchmarks")
    }

    fun getResultsFolder(): Path {
        return rootPath.resolve("benchmarksResult")
    }
    val regenerate = false

    @Test
    abstract fun checkTreeCorrectnessForGrammar()


    fun runTests(grammar :Grammar) {
        val grammarName = grammar.javaClass.simpleName
        writeRsmToDot(grammar.rsm, "${grammarName}Rsm")
        val path: Path = getDataFolder()
        val result_folder: File = File(getResultsFolder().resolve(grammarName).toUri())
        val testCasesFolder = File(path.resolve(grammarName).toUri())
        println(result_folder.toString())
        println(testCasesFolder.toString())

        if (!testCasesFolder.exists()) {
            println("Can't find test case for $grammarName")
        }
        testCasesFolder.createDirectory()
        result_folder.createDirectory()
        for (folder in testCasesFolder.listFiles()) {
            if (folder.isDirectory) {
                println(folder.name)
                println(File(Path(result_folder.path).resolve(folder.name).toUri() ))
                val bechmark_result_folder = File(Path(result_folder.path).resolve(folder.name).toUri() )
                bechmark_result_folder.createDirectory()
                testCreatedTreeForCorrectness(folder, grammar, bechmark_result_folder)
            }
        }
        assertFalse { regenerate }
    }

    fun testCreatedTreeForCorrectness(testCasesFolder: File, grammar: Grammar, result_folder: File) {
        val inputFile = testCasesFolder.toPath().resolve("input.dot")
        val input = inputFile.readText()
        val time = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val formattedDateTime = time.format(formatter)
        val logsFile = File(result_folder.toPath().toString(), formattedDateTime+"work_times" +".logs")
        val resultsLog = File(result_folder.toPath().toString(), formattedDateTime+"results" +".logs")
        val actualResult = createTree(input, grammar)
        var x = 0
        var logs = ""
        val timeMeasurements = mutableListOf<Long>()

        println("Starting performance test...")
        println("Work time: $testCasesFolder")

        while (x < 50) {
            val start = System.nanoTime()
            val actualResult = createTree(input, grammar)
            val workTime = System.nanoTime() - start
            timeMeasurements.add(workTime)
            logs += "\n$x;$workTime"

            x++
        }

        val averageTime = timeMeasurements.average()
        val minTime = timeMeasurements.minOrNull() ?: 0
        val maxTime = timeMeasurements.maxOrNull() ?: 0
        val totalTime = timeMeasurements.sum()

        println("\n=== PERFORMANCE RESULTS ===")
        println("Total iterations: ${timeMeasurements.size}")
        println("Average time: ${"%.3f".format(averageTime / 1_000_000)} ms")
        println("Min time: ${minTime / 1_000_000} ms")
        println("Max time: ${maxTime / 1_000_000} ms")
        println("Total time: ${totalTime / 1_000_000_000.0} seconds")
        println("===========================")
        logsFile.writeText(logs)
        logs = "\n=== PERFORMANCE RESULTS === \n Total iterations: ${timeMeasurements.size} \n Average time: ${"%.3f".format(averageTime / 1_000_000)} ms" +
                "\n Min time: ${minTime / 1_000_000} ms" +
                "\nMax time: ${maxTime / 1_000_000} ms" +
                "Total time: ${totalTime / 1_000_000_000.0} seconds"
        resultsLog.writeText(logs)

    }


    fun createTree(input: String, grammar: Grammar): String {
        val inputGraph = DotParser().parseDot(input)
        val gll = Gll.gll(grammar.rsm, inputGraph)
        val sppf = gll.parse()
        return getSppfDot(sppf)
    }

}