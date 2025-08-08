package solver.corectnessTests.AmbiguousAStar3GrammarTest

import org.junit.jupiter.api.Test
import solver.corectnessTests.AbstractCorrectnessTest

class AmbiguousAStar3GrammarTreeCorrectnessTest : AbstractCorrectnessTest() {
    @Test
    override fun checkTreeCorrectnessForGrammar() {
        runTests(AmbiguousAStar3Grammar())
    }
}