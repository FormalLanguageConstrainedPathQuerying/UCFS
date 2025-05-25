package org.ucfs.optbench

data class TestConfiguration(val runs: List<Pair<Int, Int>>)

data class TestSizes(val numbers: List<Int>)

fun sizesOf(vararg sizes: Int) = TestSizes(sizes.toList())

infix fun TestSizes.with(number: Int) = TestConfiguration(numbers.map { it to number })

fun configOf(vararg runs: Pair<Int, Int>) = TestConfiguration(runs.toList())

operator fun TestConfiguration.plus(other: TestConfiguration) = TestConfiguration(runs + other.runs)
