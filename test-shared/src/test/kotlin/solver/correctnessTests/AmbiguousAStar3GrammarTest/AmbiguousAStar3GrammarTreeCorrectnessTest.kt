package solver.correctnessTests.AmbiguousAStar3GrammarTest

import org.junit.jupiter.api.Test
import solver.correctnessTests.AbstractCorrectnessTest

class AmbiguousAStar3GrammarTreeCorrectnessTest : AbstractCorrectnessTest() {
    @Test
    override fun checkTreeCorrectnessForGrammar() {
        runTests(AmbiguousAStar3Grammar())
    }
}