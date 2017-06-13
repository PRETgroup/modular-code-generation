package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.finiteStateMachine.FiniteStateMachine

data class CCodeGenerator(var fsm: FiniteStateMachine, var config: Configuration = Configuration()) {
    fun generate(): CCodeGenResult {
        val generated = CCodeGenResult("", "")

        generated.h = HFileGenerator.generate(fsm, config)
        generated.c = CFileGenerator.generate(fsm, config)

        return generated
    }
}