package me.nallen.modularCodeGeneration.codeGen

import me.nallen.modularCodeGeneration.codeGen.c.CCodeGenerator
import me.nallen.modularCodeGeneration.finiteStateMachine.FiniteStateMachine

object CodeGenManager {
    fun generateStringsForFSM(fsm: FiniteStateMachine, language: CodeGenLanguage, config: Configuration = Configuration()): CodeGenResult {
        val result: CodeGenResult = when(language) {
            CodeGenLanguage.C -> CCodeGenerator(fsm, config).generate()
        }

        return result
    }

    fun generateFilesForFSM(fsm: FiniteStateMachine, dir: String, language: CodeGenLanguage) {
        val result = generateStringsForFSM(fsm, language)

        //TODO: Write the result to file(s)
    }
}

open class CodeGenResult()


enum class CodeGenLanguage {
    C
}