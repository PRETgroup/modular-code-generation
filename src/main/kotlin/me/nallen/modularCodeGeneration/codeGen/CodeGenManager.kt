package me.nallen.modularCodeGeneration.codeGen

import me.nallen.modularCodeGeneration.codeGen.c.CCodeGenResult
import me.nallen.modularCodeGeneration.codeGen.c.CCodeGenerator
import me.nallen.modularCodeGeneration.finiteStateMachine.FiniteStateMachine

/**
 * Created by Nathan on 7/06/2017.
 */

abstract class CodeGenManager {
    companion object Factory {
        fun generateStringsForFSM(fsm: FiniteStateMachine, language: CodeGenLanguage): CodeGenResult {
            val result: CodeGenResult = when(language) {
                CodeGenLanguage.C -> CCodeGenerator(fsm).generate()
            }

            return result
        }

        fun generateFilesForFSM(fsm: FiniteStateMachine, dir: String, language: CodeGenLanguage) {
            val result = generateStringsForFSM(fsm, language)

            //TODO: Write the result to file(s)
        }
    }
}

open class CodeGenResult()


enum class CodeGenLanguage {
    C
}