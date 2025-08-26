package solver.benchmarks.StrangeDyckGrammarTest

import org.junit.jupiter.api.Test
import solver.benchmarks.AbstractCorrectnessTest

class StrangeDyckGrammarTreeCorrectnessTest : AbstractCorrectnessTest() {
    @Test
    override fun checkTreeCorrectnessForGrammar() {
        runTests(StrangeDyckGrammar())
    }
}