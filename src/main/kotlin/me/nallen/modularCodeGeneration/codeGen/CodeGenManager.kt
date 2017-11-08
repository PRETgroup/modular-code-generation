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

    fun collectFieldsToLog(network: FiniteNetwork, config: Configuration): List<LoggingField> {
        val toLog = ArrayList<LoggingField>()

        if(config.logging.fields == null) {
            // Collect all "outputs" and log them
            for((name, instance) in network.instances) {
                if(network.definitions.any({it.name == instance.machine})) {
                    val definition = network.definitions.first({it.name == instance.machine})

                    val outputs = definition.variables.filter({it.locality == Locality.EXTERNAL_OUTPUT})
                    for(output in outputs) {
                        toLog.add(LoggingField(name, output.name, output.type))
                    }
                }
            }

        }
        else {
            for(field in config.logging.fields) {
                if(field.contains(".")) {
                    val machine = field.substringBeforeLast(".")
                    val variable = field.substringAfterLast(".")

                    if(network.instances.containsKey(machine)) {
                        val instance = network.instances[machine]!!
                        if(network.definitions.any({it.name == instance.machine})) {
                            val definition = network.definitions.first({it.name == instance.machine})

                            if(definition.variables.any({it.locality == Locality.EXTERNAL_OUTPUT && it.name == variable})) {
                                val output = definition.variables.first({it.locality == Locality.EXTERNAL_OUTPUT && it.name == variable})

                                toLog.add(LoggingField(machine, output.name, output.type))
                            }
                        }
                    }
                }
            }
        }

        return toLog
    }
}

data class LoggingField(val machine: String, val variable: String, val type: VariableType)

enum class CodeGenLanguage {
    C
}