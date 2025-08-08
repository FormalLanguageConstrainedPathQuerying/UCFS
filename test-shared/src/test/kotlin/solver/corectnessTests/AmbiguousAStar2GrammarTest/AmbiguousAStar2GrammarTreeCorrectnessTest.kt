package solver.corectnessTests.AmbiguousAStar2GrammarTest

import org.junit.jupiter.api.Test
import solver.corectnessTests.AbstractCorrectnessTest

class LoopDyckGrammarTreeCorrectnessTest : AbstractCorrectnessTest() {
    @Test
    override fun checkTreeCorrectnessForGrammar() {
        runTests(LoopDyckGrammar())
    }
}