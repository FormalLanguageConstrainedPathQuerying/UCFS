package solver.corectnessTests.AmbiguousAStar3GrammarTest

import org.junit.jupiter.api.Test
import solver.corectnessTests.AbstractCorrectnessTest

class StrangeDyckGrammarTreeCorrectnessTest : AbstractCorrectnessTest() {
    @Test
    override fun checkTreeCorrectnessForGrammar() {
        runTests(AmbiguiusAStart3Grammar())
    }
}