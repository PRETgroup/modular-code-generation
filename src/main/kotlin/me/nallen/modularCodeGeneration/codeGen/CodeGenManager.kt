package me.nallen.modularCodeGeneration.codeGen

import com.fasterxml.jackson.module.kotlin.*
import me.nallen.modularCodeGeneration.codeGen.c.CCodeGenerator
import me.nallen.modularCodeGeneration.finiteStateMachine.FiniteNetwork
import me.nallen.modularCodeGeneration.finiteStateMachine.FiniteStateMachine
import me.nallen.modularCodeGeneration.finiteStateMachine.Locality
import me.nallen.modularCodeGeneration.parseTree.ParseTreeItem
import java.io.File

object CodeGenManager {
    private fun generateFilesForFSM(fsm: FiniteStateMachine, language: CodeGenLanguage, dir: String, config: Configuration) {
        println("Generating ${fsm.name}")
        when(language) {
            CodeGenLanguage.C -> CCodeGenerator(fsm, config).generateFiles(dir)
        }
    }

    fun generateForNetwork(network: FiniteNetwork, language: CodeGenLanguage, dir: String, config: Configuration = Configuration()) {
        val outputDir = File(dir)

        if(outputDir.exists() && !outputDir.isDirectory)
            throw IllegalArgumentException("Desired output directory ${dir} is not a directory!")

        outputDir.deleteRecursively()
        outputDir.mkdir()

        if(config.parametrisationMethod == ParameterisationMethod.COMPILE_TIME) {
            val mapper = jacksonObjectMapper()

            for((name, instance) in network.instances) {
                // This is currently a really hacky way to do a deep copy, JSON serialize it and then deserialize.
                // Bad for performance, but easy to do. Hopefully can be fixed later?
                val json = mapper.writeValueAsString(network.definitions.first({it.name == instance.machine}))

                val fsm = mapper.readValue<FiniteStateMachine>(json)
                fsm.name = name

                for((key, value) in instance.parameters) {
                    fsm.setParameterValue(key, value)
                }

                generateFilesForFSM(fsm, language, File(outputDir, instance.machine).absolutePath, config)
            }
        }
        else  {
            val generated = ArrayList<String>()

            for((_, instance) in network.instances) {
                if (!generated.contains(instance.machine)) {
                    generated.add(instance.machine)

                    val fsm = network.definitions.first({ it.name == instance.machine })
    
                    generateFilesForFSM(fsm, language, outputDir.absolutePath, config)
                }
            }
        }
    }
}

enum class CodeGenLanguage {
    C
}