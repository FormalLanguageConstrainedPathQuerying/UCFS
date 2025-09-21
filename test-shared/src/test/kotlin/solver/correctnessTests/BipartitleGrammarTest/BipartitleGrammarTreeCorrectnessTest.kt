package solver.correctnessTests.BipartitleGrammarTest

import org.junit.jupiter.api.Test
import solver.correctnessTests.AbstractCorrectnessTest

class BipartitleGrammarTreeCorrectnessTest : AbstractCorrectnessTest() {
    @Test
    override fun checkTreeCorrectnessForGrammar() {
        runTests(BipartitleGrammar())
    }
}