package solver.StrangeDyckGrammarTest

import org.junit.jupiter.api.Test
import solver.AbstractCorrectnessTest

class StrangeDyckGrammarTreeCorrectnessTest : AbstractCorrectnessTest() {
    @Test
    override fun checkTreeCorrectnessForGrammar() {
        runTest(StrangeDyckGrammar())
    }
}