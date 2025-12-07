package solver.correctnessTests.dyckKParityAlpha

import org.junit.jupiter.api.Test
import solver.correctnessTests.AbstractCorrectnessTest

class DyckKParityTest : AbstractCorrectnessTest() {
    @Test
    override fun checkTreeCorrectnessForGrammar() {
        runTests(dyckAlphaGrammarKParity())
        runTests(dyckBetaGrammarKParity())
    }
}