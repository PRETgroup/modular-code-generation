package me.nallen.modularCodeGeneration.codeGen

import me.nallen.modularCodeGeneration.codeGen.c.CCodeGenerator
import me.nallen.modularCodeGeneration.finiteStateMachine.FiniteNetwork
import me.nallen.modularCodeGeneration.finiteStateMachine.FiniteStateMachine
import me.nallen.modularCodeGeneration.hybridAutomata.HybridNetwork

object CodeGenManager {
    private fun generateStringsForFSM(fsm: FiniteStateMachine, language: CodeGenLanguage, config: Configuration): CodeGenResult {
        val result: CodeGenResult = when(language) {
            CodeGenLanguage.C -> CCodeGenerator(fsm, config).generate()
        }

        return result
    }

    private fun generateFilesForFSM(fsm: FiniteStateMachine, language: CodeGenLanguage, dir: String, config: Configuration) {
        val result = generateStringsForFSM(fsm, language, config)

        //TODO: Write the result to file(s)
    }

    fun generateForHybridNetwork(network: FiniteNetwork, language: CodeGenLanguage, dir: String, config: Configuration = Configuration()) {

    }
}

open class CodeGenResult()


enum class CodeGenLanguage {
    C
}