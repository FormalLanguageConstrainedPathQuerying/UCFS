package solver.benchmarks.LoopDyckGrammarTest

import org.junit.jupiter.api.Test
import solver.benchmarks.AbstractCorrectnessTest

class LoopDyckGrammarTreeCorrectnessTest : AbstractCorrectnessTest() {
    @Test
    override fun checkTreeCorrectnessForGrammar() {
        runTests(LoopDyckGrammar())
    }
}