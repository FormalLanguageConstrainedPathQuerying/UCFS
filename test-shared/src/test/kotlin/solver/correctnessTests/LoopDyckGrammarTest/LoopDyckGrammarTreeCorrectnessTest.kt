package solver.correctnessTests.LoopDyckGrammarTest

import org.junit.jupiter.api.Test
import solver.correctnessTests.AbstractCorrectnessTest

class LoopDyckGrammarTreeCorrectnessTest : AbstractCorrectnessTest() {
    @Test
    override fun checkTreeCorrectnessForGrammar() {
        runTests(LoopDyckGrammar())
    }
}