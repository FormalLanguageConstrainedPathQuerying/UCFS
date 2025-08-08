package solver.corectnessTests.SimplifiedDyckGrammarTest

import org.junit.jupiter.api.Test
import solver.corectnessTests.AbstractCorrectnessTest

class SimplifiedDyckGrammarTreeCorrectnessTest : AbstractCorrectnessTest() {
    @Test
    override fun checkTreeCorrectnessForGrammar() {
        runTests(SimplifiedDyckGrammar())
    }
}