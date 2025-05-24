package org.ucfs.input

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.ucfs.input.utils.dot.DotLexer
import org.ucfs.input.utils.dot.DotParser
import org.ucfs.input.utils.dot.GraphFromDotVisitor
import java.io.File
import java.io.IOException

class DotParser {
    fun parseDotFile(filePath: String): InputGraph<Int> {
        val file = File(filePath)

        if (!file.exists()) {
            throw IOException("File not found: $filePath")
        }
        return parseDot(file.readText())
    }

    fun parseDot(dotView: String): InputGraph<Int> {
        val realParser =
            DotParser(
                CommonTokenStream(
                    DotLexer(
                        CharStreams.fromString(dotView),
                    ),
                ),
            )
        return GraphFromDotVisitor().visitGraph(realParser.graph())
    }
}
