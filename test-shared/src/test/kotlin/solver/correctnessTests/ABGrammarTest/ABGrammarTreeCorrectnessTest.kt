package solver.correctnessTests.ABGrammarTest

import org.junit.jupiter.api.Test
import solver.correctnessTests.AbstractCorrectnessTest

class ABGrammarTreeCorrectnessTest : AbstractCorrectnessTest() {
    @Test
    override fun checkTreeCorrectnessForGrammar() {
        runTests(ABGrammar())
    }
}