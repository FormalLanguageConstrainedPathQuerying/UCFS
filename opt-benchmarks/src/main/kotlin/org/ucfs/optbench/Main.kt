package org.ucfs.optbench

import org.ucfs.optbench.testsource.*
import java.io.File

object Configurations {
    val longBig = sizesOf(500, 1000, 2000, 5000, 10000, 20000, 50000) with 200
    val longBigCapped = sizesOf(500, 1000, 5000, 10000, 15000) with 200
    val longMedium = sizesOf(500, 1000, 2000, 5000, 10000, 20000) with 100
    val longSmall = sizesOf(500, 1000, 2000, 5000, 10000, 20000, 50000) with 25
    val longTiny = sizesOf(500, 1000, 5000, 10000, 15000) with 25
    val shortBig = sizesOf(100, 200, 300, 400, 500, 800, 1000) with 200
    val shortMedium = sizesOf(100, 200, 300, 400, 500) with 100
    val shortSmall = sizesOf(100, 200, 300, 400, 500) with 25
    val shortTiny = sizesOf(100, 200, 300, 400, 500) with 10
    val veryShortTiny = sizesOf(50, 60, 70, 80, 90) with 10
}

object Generators {
    val DyckAccept = DyckAcceptTestGenerator()
    val DyckReject = DyckRejectTestGenerator()
    val Dyck3Accept = Dyck3AcceptTestGenerator()
    val Dyck3Reject = Dyck3RejectTestGenerator()
    val ExpressionAccept = ExpressionAcceptTestGenerator()
    val PalindromeAccept = PalindromeAcceptTestGenerator()
    val PalindromeReject = PalindromeRejectTestGenerator()
    val UnequalAccept = UnequalBlocksAcceptTestGenerator()
    val UnequalReject = UnequalBlocksRejectTestGenerator()
    val NonSquareAccept = NonSquareAcceptTestGenerator()
    val NonSquareReject = NonSquareRejectTestGenerator()
    val AStartAccept = StrangeAStarAcceptTestGenerator()
    val AStartReject = StrangeAStarRejectTestGenerator()

    val fast = listOf(DyckAccept, DyckReject, Dyck3Accept, Dyck3Reject, ExpressionAccept)
    val medium = listOf(PalindromeAccept, PalindromeReject)
    val slow = listOf(UnequalAccept, NonSquareAccept, UnequalReject, NonSquareReject)
    val verySlow = listOf(AStartAccept, AStartReject)
}

fun main() {
    benchmark {
        warmup()
        benchMany(Generators.fast, Configurations.longBig)
        benchMany(Generators.medium, Configurations.longBigCapped)
        benchMany(Generators.slow, Configurations.shortMedium)
    }.dump(File("optimized.csv"))
}
