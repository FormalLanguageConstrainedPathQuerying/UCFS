package solver.benchmarks.StrangeDyckGrammarTest

import org.junit.jupiter.api.Test
import solver.benchmarks.AbstractBenchmarkTest

class StrangeDyckGrammarTreeBenchmarkTest : AbstractBenchmarkTest() {
    @Test
    override fun checkTreeCorrectnessForGrammar() {
        runTests(StrangeDyckGrammar())
    }
}