package solver.correctnessTests.SimplifiedDyckGrammarTest

import org.junit.jupiter.api.Test
import solver.correctnessTests.AbstractCorrectnessTest

class SimplifiedDyckGrammarTreeCorrectnessTest : AbstractCorrectnessTest() {
    @Test
    override fun checkTreeCorrectnessForGrammar() {
        runTests(SimplifiedDyckGrammar())
    }
}