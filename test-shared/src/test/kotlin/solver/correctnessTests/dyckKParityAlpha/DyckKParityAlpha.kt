package solver.correctnessTests.dyckKParityAlpha

import org.ucfs.grammar.combinator.Grammar
import org.ucfs.grammar.combinator.regexp.Epsilon
import org.ucfs.grammar.combinator.regexp.Nt
import org.ucfs.grammar.combinator.regexp.Regexp
import org.ucfs.grammar.combinator.regexp.or
import org.ucfs.grammar.combinator.regexp.times
import org.ucfs.rsm.symbol.Nonterminal
import org.ucfs.rsm.symbol.Term

private fun maskToParity(mask: Int, k: Int): String {
    val sb = StringBuilder()

    for (i in 0 until k) {
        if (((mask shr i) % 2) == 0) {
            sb.append("p")
        } else {
            sb.append("i")
        }
    }

    return sb.toString()
}

private fun initNtReflectively(grammar: Grammar, nt: Nt, name: String) {
    val ntClass = Nt::class.java

    val nameField = ntClass.getDeclaredField("name")
    nameField.isAccessible = true
    nameField.set(nt, name)

    val nontermField = ntClass.getDeclaredField("nonterm")
    nontermField.isAccessible = true
    nontermField.set(nt, Nonterminal(name))

    grammar.nonTerms.add(nt)
}

fun dyckAlphaGrammarKParity(
    parenthesesIds: List<String> = listOf("1"),
    bracketsIds: List<String> = listOf("1"),
    k: Int = 1
): Grammar {
    return object : Grammar() {
        val S by Nt().asStart()

        init {
            val grammar = this
            val numStates = 1 shl k // 2^k - Total number of parity states

            // Create 2^k non-terminals (states)
            // Each state S_mask corresponds to a row where the parity
            // of the k brackets is equal to the 'mask'
            val Smap = buildMap<Int, Nt> {
                for (mask in 0 until numStates) {
                    val p = maskToParity(mask, k)
                    val nt = Nt()
                    initNtReflectively(grammar, nt, "S$p")
                    put(mask, nt)
                }
            }

            val sortedB = bracketsIds.sorted()
            val labelGroup = mutableMapOf<String, Int>()
            // Assign each group of brackets the corresponding bit (group)
            for ((i, label) in sortedB.withIndex()) {
                labelGroup[label] = i % k
            }

            for (currentMask in 0 until numStates) {
                val currentNt = Smap.getValue(currentMask)
                val alternatives = mutableListOf<Regexp>()

                // Exit Rule (Eq 10)
                if (currentMask == 0) {
                    alternatives.add(Epsilon)
                }

                // Rules for BRACKETS - Beta (Eq 12, 13)
                // Brackets consume a token and change state (XOR by 1 bit)
                // Rule: Target -> Terminal * Next, where Target = Bit ^ Next
                for (brId in sortedB) {
                    val group = labelGroup.getValue(brId)
                    val bit = 1 shl group

                    val nextMask = currentMask xor bit
                    val nextNt = Smap.getValue(nextMask)

                    val open = Term("ob--$brId")
                    val close = Term("cb--$brId")

                    alternatives.add(open * nextNt)
                    alternatives.add(close * nextNt)
                }

                // Rules for PARENTHESES - Alpha (Eq 11 + 14)
                // Parentheses are transparent (do not add their parity) and contain a substring.
                // Rule: Target -> ( Inner ) * Next, where Target = Inner ^ Next
                for (parId in parenthesesIds) {
                    val open = Term("op--$parId")
                    val close = Term("cp--$parId")

                    // We iterate over all possible masks for the content INSIDE the parentheses (innerMask)
                    for (innerMask in 0 until numStates) {
                        val S_inner = Smap.getValue(innerMask)

                        val nextMask = currentMask xor innerMask
                        val S_next = Smap.getValue(nextMask)

                        alternatives.add((open * S_inner * close) * S_next)
                    }
                }

                alternatives.add(Term("normal") * currentNt)

                // Combine all alternatives into one rule for the current non-terminal
                if (alternatives.isNotEmpty()) {
                    var combined = alternatives[0]
                    for (i in 1 until alternatives.size) {
                        combined = combined or alternatives[i]
                    }
                    currentNt /= combined
                }
            }

            // The starting rule S must lead to a fully balanced state (mask 0)
            S /= Smap.getValue(0)
        }
    }
}

fun dyckBetaGrammarKParity(
    parenthesesIds: List<String> = listOf("1"),
    bracketsIds: List<String> = listOf("1"),
    k: Int = 1
): Grammar {
    return object : Grammar() {
        val S by Nt().asStart()

        init {
            val grammar = this
            val numStates = 1 shl k  // 2^k - Total number of parity states

            // Create 2^k non-terminals (states)
            // Each state S_mask corresponds to a row where the parity
            // of the k parentheses is equal to the 'mask'
            val Smap = buildMap<Int, Nt> {
                for (mask in 0 until numStates) {
                    val p = maskToParity(mask, k)
                    val nt = Nt()
                    initNtReflectively(grammar, nt, "S$p")
                    put(mask, nt)
                }
            }

            val sortedP = parenthesesIds.sorted()
            val labelGroup = mutableMapOf<String, Int>()
            // Assign each group of parentheses the corresponding bit (group)
            for ((i, label) in sortedP.withIndex()) {
                labelGroup[label] = i % k
            }

            for (currentMask in 0 until numStates) {
                val currentNt = Smap.getValue(currentMask)
                val alternatives = mutableListOf<Regexp>()

                // Exit Rule (Eq 10)
                if (currentMask == 0) {
                    alternatives.add(Epsilon)
                }

                // Rules for PARENTHESES - analogically Beta (Eq 12, 13)
                // Parentheses consume a token and change state (XOR by 1 bit)
                // Rule: Target -> Terminal * Next, where Target = Bit ^ Next
                for (parId in sortedP) {
                    val group = labelGroup.getValue(parId)
                    val bit = 1 shl group

                    val nextMask = currentMask xor bit
                    val nextNt = Smap.getValue(nextMask)

                    val open = Term("op--$parId")
                    val close = Term("cp--$parId")

                    alternatives.add(open * nextNt)
                    alternatives.add(close * nextNt)
                }

                // Rules for BRACKETS - analogically Alpha (Eq 11 + 14)
                // Brackets are transparent (do not add their parity) and contain a substring.
                // Rule: Target -> ( Inner ) * Next, where Target = Inner ^ Next
                for (brId in bracketsIds) {
                    val open = Term("ob--$brId")
                    val close = Term("cb--$brId")

                    // We iterate over all possible masks for the content INSIDE the brackets (innerMask)
                    for (innerMask in 0 until numStates) {
                        val S_inner = Smap.getValue(innerMask)

                        val nextMask = currentMask xor innerMask
                        val S_next = Smap.getValue(nextMask)

                        alternatives.add((open * S_inner * close) * S_next)
                    }
                }

                alternatives.add(Term("normal") * currentNt)

                // Combine all alternatives into one rule for the current non-terminal
                if (alternatives.isNotEmpty()) {
                    var combined = alternatives[0]
                    for (i in 1 until alternatives.size) {
                        combined = combined or alternatives[i]
                    }
                    currentNt /= combined
                }
            }

            // The starting rule S must lead to a fully balanced state (mask 0)
            S /= Smap.getValue(0)
        }
    }
}
