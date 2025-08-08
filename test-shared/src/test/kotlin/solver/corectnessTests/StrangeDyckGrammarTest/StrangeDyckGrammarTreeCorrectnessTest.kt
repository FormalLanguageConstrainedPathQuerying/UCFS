package solver.corectnessTests.StrangeDyckGrammarTest

import org.junit.jupiter.api.Test
import solver.corectnessTests.AbstractCorrectnessTest

class StrangeDyckGrammarTreeCorrectnessTest : AbstractCorrectnessTest() {
    @Test
    override fun checkTreeCorrectnessForGrammar() {
        runTests(StrangeDyckGrammar())
    }
}