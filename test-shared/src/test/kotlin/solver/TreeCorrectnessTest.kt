package solver

import java.io.File
import org.ucfs.grammar.combinator.Grammar
import org.ucfs.input.DotParser
import org.ucfs.parser.Gll
import org.ucfs.sppf.getSppfDot
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TreeCorrectnessTest : AbstractCorrectnessTest() {
    override fun getRootDataFolder(): Path {
        return rootPath.resolve("tree")
    }

    override fun runGoldTest(testCasesFolder: File, grammar: Grammar) {
        val inputFile = testCasesFolder.toPath().resolve("input.dot")
        val expectedFile = testCasesFolder.toPath().resolve("result.dot")
        val input = inputFile.readText()
        val expectedResult = expectedFile.readText()
        val actualResult = runTest(input, grammar)
        if (expectedResult.isEmpty() || regenerate) {
            expectedFile.writeText(actualResult)
        } else {
            assertEquals(expectedResult, actualResult)
        }
    }


    fun runTest(input: String, grammar: Grammar): String {
        val inputGraph = DotParser().parseDot(input)
        val gll = Gll.gll(grammar.rsm, inputGraph)
        val sppf = gll.parse()
        assertNotNull(sppf) { "Can't parse input!" }
        return getSppfDot(sppf)
    }
}