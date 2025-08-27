package solver.benchmarks.CAliasTest

import org.junit.jupiter.api.Test
import solver.benchmarks.AbstractBenchmarkTest

class CAliasBenchmarkTest : AbstractBenchmarkTest() {
    @Test
    override fun checkTreeCorrectnessForGrammar() {
        runTests(CAliasGrammar())
    }
}