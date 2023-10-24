package org.srcgll.benchmarks

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.srcgll.GLL
import org.srcgll.grammar.readRSMFromTXT
import org.srcgll.input.Graph
import org.srcgll.lexer.GeneratedLexer
import org.srcgll.lexer.SymbolCode
import org.srcgll.lexer.Token
import org.srcgll.sppf.ISPPFNode
import org.srcgll.sppf.SPPFNode
import org.srcgll.sppf.toDot
import java.io.File
import java.io.StringReader
import kotlin.system.measureNanoTime

fun getResultPath
(
    pathToOutput : String,
    inputName    : String,
    grammarMode  : String,
    grammarName  : String,
    sppfMode     : String,
)
    : String
{
    return pathToOutput +
            (if (pathToOutput.endsWith("/")) "" else "/") +
            "${inputName}_${grammarMode}_${grammarName}_${sppfMode}.csv"
}

fun main(args : Array<String>)
{
    val parser = ArgParser("srcgll.benchmarks")

    val pathToInput by
    parser
        .option(
            ArgType.String, fullName = "inputPath", description = "Path to folder with inputs"
        )
        .required()
    val pathToGrammar by
    parser
        .option(
            ArgType.String, fullName = "grammarPath", description = "Path to grammar txt file"
        )
        .required()
    val pathToOutput by
    parser
        .option(
            ArgType.String, fullName = "outputPath", description = "Path to folder with results"
        )
        .required()
    val warmUpRounds by
    parser
        .option(ArgType.Int, fullName = "warmUpRounds", description = "Number of warm-up rounds")
        .default(3)
    val benchmarksRounds by
    parser
        .option(
            ArgType.Int, fullName = "benchmarkRounds", description = "Number of benchmark rounds"
        )
        .default(10)

    parser.parse(args)

    runRSMWithSPPF(pathToInput, pathToGrammar, pathToOutput, warmUpRounds, benchmarksRounds)
}

fun runRSMWithSPPF
(
    pathToInput     : String,
    pathToRSM       : String,
    pathToOutput    : String,
    warmUpRounds    : Int,
    benchmarkRounds : Int,
)
{
    val rsm     = readRSMFromTXT(pathToRSM)
    val rsmName = File(pathToRSM).nameWithoutExtension

    File(pathToInput)
        .walk()
        .filter { it.isFile }
        .forEach { inputPath ->
            val inputName = inputPath.nameWithoutExtension
            println("start:: $inputName")
            val input = File(inputPath.path).readText()

            val resultPath = getResultPath(pathToOutput, inputName, "rsm", rsmName, "with_sppf")
            File(resultPath).writeText("")

            val inputGraph : Graph<Int, Token<SymbolCode>> = Graph()
            val lexer : GeneratedLexer = GeneratedLexer(StringReader(input))
            var token : Token<SymbolCode>
            var vertexId = 1

            inputGraph.addVertex(vertexId)
            inputGraph.startVertex = vertexId

            while (!lexer.yyatEOF()) {
                token = lexer.yylex() as Token<SymbolCode>
                inputGraph.addEdge(vertexId, token, ++vertexId)
            }

            inputGraph.finalVertex = vertexId - 1

            var result : ISPPFNode? = GLL(rsm, inputGraph, recovery = true).parse()
            toDot(result!!, "./outputFiles/${inputName}_sppf.dot")

            for (warmUp in 1 .. warmUpRounds)
            {
                var result : SPPFNode<Int>?
                val elapsedRecovery = measureNanoTime {
                    result = GLL(rsm, inputGraph, recovery = true).parse()
                }

                val elapsedRecoverySeconds = elapsedRecovery.toDouble() / 1_000_000_000.0

                println("warmup:: $inputName $rsmName $elapsedRecoverySeconds")
            }

            var totalRecoveryTime = 0.0

            for (benchmarkAttempt in 1 .. benchmarkRounds)
            {
                var result : SPPFNode<Int>?

                val elapsedRecovery = measureNanoTime {
                    result = GLL(rsm, inputGraph, recovery = true).parse()
                }

                val elapsedRecoverySeconds = elapsedRecovery.toDouble() / 1_000_000_000.0

                totalRecoveryTime += elapsedRecoverySeconds

                println("benchmark:: $inputName $elapsedRecoverySeconds")
                File(resultPath).appendText("${input.length} ::: $elapsedRecoverySeconds\n")
            }
            val averageRecoveryTime = totalRecoveryTime / benchmarkRounds

            File(resultPath).appendText("totalRecoveryTime: $totalRecoveryTime seconds\n")
            File(resultPath).appendText("averageRecoveryTime : $averageRecoveryTime seconds\n")
        }
}
