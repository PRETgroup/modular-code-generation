package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.finiteStateMachine.FiniteStateMachine
import me.nallen.modularCodeGeneration.parseTree.ParseTreeItem
import java.io.File

data class CCodeGenerator(var fsm: FiniteStateMachine, var config: Configuration = Configuration()) {
    fun generateFiles(dir: String) {
        val outputDir = File(dir)

        if(!outputDir.exists())
            outputDir.mkdir()

        File(outputDir, "${fsm.name}.h").writeText(HFileGenerator.generate(fsm, config))
        File(outputDir, "${fsm.name}.c").writeText(CFileGenerator.generate(fsm, config))
    }
}