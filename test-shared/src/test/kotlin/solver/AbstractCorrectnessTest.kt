package solver

import grammars.*
import org.jetbrains.kotlin.incremental.createDirectory
import org.junit.jupiter.api.Test
import org.ucfs.grammar.combinator.Grammar
import org.ucfs.rsm.writeRsmToDot
import java.io.File
import java.nio.file.Path

abstract class AbstractCorrectnessTest {
    val rootPath: Path = Path.of("src", "test", "resources", "correctness")

    abstract fun getRootDataFolder(): Path

    val grammars = listOf(SimplifiedDyck(), ABGrammar(), SALang(), Epsilon(), LoopDyck())

    //@TestFactory
    //TODO make it abstract by used grammar
    @Test
    fun testCorrectness() {
        for (grammar in grammars) {
            val grammarName = grammar.javaClass.simpleName
            writeRsmToDot(grammar.rsm, "${grammarName}Rsm")
            val path: Path = getRootDataFolder()
            val testCasesFolder = File(path.resolve(grammarName).toUri())
            if (!testCasesFolder.exists()) {
                println("Can't find test case for $grammarName")
            }
            testCasesFolder.createDirectory()
            for (folder in testCasesFolder.listFiles()) {
                if (folder.isDirectory) {
                    runGoldTest(folder, grammar)
                }
            }
        }

    }

    abstract fun runGoldTest(testCasesFolder: File, grammar: Grammar)
}