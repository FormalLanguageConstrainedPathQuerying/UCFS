package solver.corectnessTests

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
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

abstract class AbstractCorrectnessTest {
    val rootPath: Path = Path.of("src", "test", "resources", "correctness")

    fun getRootDataFolder(): Path {
        return rootPath.resolve("tree")
    }

    val regenerate = false

    @Test
    abstract fun checkTreeCorrectnessForGrammar()


    fun runTests(grammar :Grammar) {
        val grammarName = grammar.javaClass.simpleName
        writeRsmToDot(grammar.rsm, "${grammarName}Rsm")
        val path: Path = getRootDataFolder()
        val testCasesFolder = File(path.resolve(grammarName).toUri())

        if (!testCasesFolder.exists()) {
            println("Can't find test case for $grammarName")
        }
        testCasesFolder.createDirectory()
        for (folder in testCasesFolder.listFiles()) {
            if (folder.isDirectory) {
                testCreatedTreeForCorrectness(folder, grammar)
            }
        }
        assertFalse { regenerate }
    }

    fun testCreatedTreeForCorrectness(testCasesFolder: File, grammar: Grammar) {
        val inputFile = testCasesFolder.toPath().resolve("input.dot")
        val expectedFile = testCasesFolder.toPath().resolve("result.dot")
        val input = inputFile.readText()
        val expectedResult = expectedFile.readText()
        val actualResult = createTree(input, grammar)
        if (expectedResult.isEmpty() || regenerate) {
            expectedFile.writeText(actualResult)
        } else {
            assertEquals(
                expectedResult,
                actualResult,
                "for grammar ${grammar.javaClass.simpleName} at ${testCasesFolder.name}"
            )
        }
    }


    fun createTree(input: String, grammar: Grammar): String {
        val inputGraph = DotParser().parseDot(input)
        val gll = Gll.gll(grammar.rsm, inputGraph)
        val sppf = gll.parse()
        assertNotNull(sppf) { "Can't parse input!" }
        return getSppfDot(sppf)
    }

}