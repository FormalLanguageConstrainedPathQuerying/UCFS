package solver.corectnessTests.SALangGrammarTest

import org.junit.jupiter.api.Test
import solver.corectnessTests.AbstractCorrectnessTest

class EpsilonGrammarTreeCorrectnessTest : AbstractCorrectnessTest() {
    @Test
    override fun checkTreeCorrectnessForGrammar() {
        runTests(SALangGrammar())
    }
}