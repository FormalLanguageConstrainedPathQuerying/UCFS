package solver.SimplifiedDyckGrammarTest

import org.jetbrains.kotlin.incremental.createDirectory
import org.junit.jupiter.api.Test
import java.io.File
import org.ucfs.input.DotParser
import org.ucfs.parser.Gll
import org.ucfs.rsm.writeRsmToDot
import org.ucfs.sppf.getSppfDot
import solver.AbstractCorrectnessTest

class SimplifiedDyckGrammarTreeCorrectnessTest : AbstractCorrectnessTest() {
    @Test
    override fun checkTreeCorrectnessForGrammar() {
        runTest(SimplifiedDyckGrammar())
    }
}