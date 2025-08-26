package solver.benchmarks.BipartitleGrammarTest

import org.junit.jupiter.api.Test
import solver.benchmarks.AbstractCorrectnessTest

class BipartitleGrammarTreeCorrectnessTest : AbstractCorrectnessTest() {
    @Test
    override fun checkTreeCorrectnessForGrammar() {
        runTests(BipartitleGrammar())
    }
}