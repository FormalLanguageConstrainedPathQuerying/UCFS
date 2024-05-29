package org

import kotlinx.benchmark.*
import org.ucfs.input.LinearInputLabel


@State(Scope.Benchmark)
class OfflineGll : BaseBench() {

    @Benchmark
    fun measureGll(blackhole: Blackhole) {
        val parser = org.ucfs.Java7Parser<Int, LinearInputLabel>()
        parser.input = getTokenStream(fileContents)
        blackhole.consume(parser.parse())
    }
}
