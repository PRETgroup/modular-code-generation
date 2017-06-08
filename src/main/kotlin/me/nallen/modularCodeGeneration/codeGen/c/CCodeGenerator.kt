package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.CodeGenLanguage
import me.nallen.modularCodeGeneration.codeGen.CodeGenResult
import me.nallen.modularCodeGeneration.finiteStateMachine.FiniteStateMachine

/**
 * Created by Nathan on 7/06/2017.
 */

data class CCodeGenerator(var fsm: FiniteStateMachine) {
    fun generate(): CCodeGenResult {
        val generated = CCodeGenResult("", "")

        generated.h = HFileGenerator.generate(fsm)
        generated.c = CFileGenerator.generate(fsm)

        return generated
    }
}

data class CCodeGenResult(var c: String, var h: String): CodeGenResult()