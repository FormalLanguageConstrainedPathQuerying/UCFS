package solver.correctnessTests.dyckKParity

import org.junit.jupiter.api.Test
import solver.correctnessTests.AbstractCorrectnessTest

class TestDyckKParityBeta : AbstractCorrectnessTest() {
    @Test
    override fun checkTreeCorrectnessForGrammar() {
        runTests(dyckBetaGrammarKParity())
    }
}