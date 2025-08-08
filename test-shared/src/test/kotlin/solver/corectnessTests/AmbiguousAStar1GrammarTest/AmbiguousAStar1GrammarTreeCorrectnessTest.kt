package solver.corectnessTests.AmbiguousAStar1GrammarTest

import org.junit.jupiter.api.Test
import solver.corectnessTests.AbstractCorrectnessTest

class AmbiguousAStar2GrammarTreeCorrectnessTest : AbstractCorrectnessTest() {
    @Test
    override fun checkTreeCorrectnessForGrammar() {
        runTests(AmbiguousAStar1Grammar())
    }
}