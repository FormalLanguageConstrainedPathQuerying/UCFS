package dynamic.parser.generator

import dynamic.parser.IOfflineGllTest
import dynamic.parser.generator.compilation.RuntimeCompiler
import org.srcgll.input.LinearInputLabel
import org.srcgll.parser.generator.GeneratedParser
import java.io.File


open class GllGeneratedTest : IOfflineGllTest {
    companion object {
        const val DSL_FILE_NAME = "GrammarDsl"
        const val TOKENS_FILE_NAME = "Token"
    }

    override val mainFileName: String
        get() = "$DSL_FILE_NAME.kt"


    override fun getGll(concreteGrammarFolder: File): GeneratedParser<Int, LinearInputLabel> {
        val grammarName = concreteGrammarFolder.name
        return RuntimeCompiler.generateParser(concreteGrammarFolder, grammarName)
    }

}
