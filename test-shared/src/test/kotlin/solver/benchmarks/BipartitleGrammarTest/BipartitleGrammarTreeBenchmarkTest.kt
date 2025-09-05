package solver.benchmarks.BipartitleGrammarTest

import org.junit.jupiter.api.Test
import solver.benchmarks.AbstractBenchmarkTest

class BipartitleGrammarTreeBenchmarkTest : AbstractBenchmarkTest() {
    @Test
    override fun checkTreeCorrectnessForGrammar() {
        runTests(BipartitleGrammar())
    }
}