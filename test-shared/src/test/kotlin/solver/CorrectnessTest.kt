package solver

import grammars.SimpleDyck
import org.jetbrains.kotlin.incremental.createDirectory
import java.io.File
import org.junit.jupiter.api.Test
import org.ucfs.grammar.combinator.Grammar
import org.ucfs.input.DotParser
import org.ucfs.parser.Gll
import org.ucfs.rsm.writeRsmToDot
import org.ucfs.sppf.getSppfDot
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CorrectnessTest {

    //@TestFactory
    @Test
    fun testCorrectness() {
        val grammar = SimpleDyck()
        writeRsmToDot(grammar.rsm, "bob2")
        val grammarName = grammar.javaClass.simpleName
        val testCasesFolder = File(Path.of("src", "test", "resources", "correctness", grammarName).toUri())
        if (!testCasesFolder.exists()) {
            println("Can't find test case for $grammarName")
        }
        testCasesFolder.createDirectory()
        for (folder in testCasesFolder.listFiles()) {
            if (folder.isDirectory) {
                runGoldTest(folder, grammar)
            }
        }

    }

    fun runGoldTest(folder: File, grammar: Grammar) {
        val inputFile = folder.toPath().resolve("input.dot")
        val expectedFile = folder.toPath().resolve("result.dot")
        val input = inputFile.readText()
        val expectedResult = expectedFile.readText()
        val actualResult = runTest(input, grammar)
        if (expectedResult.isEmpty()) {
            expectedFile.writeText(actualResult)
        } else {
            assertEquals(expectedResult, actualResult)
        }
    }


    fun runTest(input: String, grammar: Grammar): String {
        val inputGraph = DotParser().parseDot(input)
        val sppf = Gll.gll(grammar.rsm, inputGraph).parse()
        assertNotNull(sppf) { "Can't parse input!" }
        return getSppfDot(sppf)
    }
}