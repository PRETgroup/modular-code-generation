package me.nallen.modularCodeGeneration.codeGen

import com.fasterxml.jackson.module.kotlin.*
import me.nallen.modularCodeGeneration.codeGen.c.CCodeGenerator
import me.nallen.modularCodeGeneration.finiteStateMachine.*
import java.io.File

object CodeGenManager {
    private val mapper = jacksonObjectMapper()
    
    fun generateForNetwork(network: FiniteNetwork, language: CodeGenLanguage, dir: String, config: Configuration = Configuration()) {
        val outputDir = File(dir)

        if(outputDir.exists() && !outputDir.isDirectory)
            throw IllegalArgumentException("Desired output directory ${dir} is not a directory!")

        outputDir.deleteRecursively()
        outputDir.mkdir()

        when(language) {
            CodeGenLanguage.C -> CCodeGenerator.generateNetwork(network, dir, config)
        }
    }

    fun createParametrisedFsm(network: FiniteNetwork, name: String, instance: FiniteInstance): FiniteStateMachine? {
        if(network.definitions.any({it.name == instance.machine})) {
            // This is currently a really hacky way to do a deep copy, JSON serialize it and then deserialize.
            // Bad for performance, but easy to do. Hopefully can be fixed later?
            val json = mapper.writeValueAsString(network.definitions.first({ it.name == instance.machine }))

            val fsm = mapper.readValue<FiniteStateMachine>(json)
            fsm.name = name

            for ((key, value) in instance.parameters) {
                fsm.setParameterValue(key, value)
            }

            fsm.setDefaultParametrisation()

            return fsm
        }

        return null
    }
}

enum class CodeGenLanguage {
    C
}