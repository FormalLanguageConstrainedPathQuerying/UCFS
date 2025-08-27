package solver.benchmarks.LoopDyckGrammarTest

import org.junit.jupiter.api.Test
import solver.benchmarks.AbstractBenchmarkTest

class LoopDyckGrammarTreeBenchmarkTest : AbstractBenchmarkTest() {
    @Test
    override fun checkTreeCorrectnessForGrammar() {
        runTests(LoopDyckGrammar())
    }
}