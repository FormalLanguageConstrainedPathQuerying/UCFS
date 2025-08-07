package solver.StrangeDyckGrammarTest

import org.junit.jupiter.api.Test
import solver.AbstractCorrectnessTest
import java.io.File

class StrangeDyckGrammarTreeCorrectnessTest : AbstractCorrectnessTest() {
    @Test
    override fun checkTreeCorrectnessForGrammar() {
        runTests(StrangeDyckGrammar())
    }
}