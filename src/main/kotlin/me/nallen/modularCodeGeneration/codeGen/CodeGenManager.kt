package me.nallen.modularCodeGeneration.codeGen

import com.fasterxml.jackson.module.kotlin.*
import me.nallen.modularCodeGeneration.codeGen.c.CCodeGenerator
import me.nallen.modularCodeGeneration.hybridAutomata.AutomataInstance
import me.nallen.modularCodeGeneration.hybridAutomata.HybridAutomata
import me.nallen.modularCodeGeneration.hybridAutomata.HybridNetwork
import me.nallen.modularCodeGeneration.hybridAutomata.Locality
import me.nallen.modularCodeGeneration.parseTree.VariableDeclaration
import me.nallen.modularCodeGeneration.parseTree.VariableType
import java.io.File

typealias ParseTreeLocality = me.nallen.modularCodeGeneration.parseTree.Locality

object CodeGenManager {
    private val mapper = jacksonObjectMapper()
    
    fun generateForNetwork(network: HybridNetwork, language: CodeGenLanguage, dir: String, config: Configuration = Configuration()) {
        val outputDir = File(dir)

        if(outputDir.exists() && !outputDir.isDirectory)
            throw IllegalArgumentException("Desired output directory ${dir} is not a directory!")

        outputDir.deleteRecursively()
        outputDir.mkdir()

        when(language) {
            CodeGenLanguage.C -> CCodeGenerator.generateNetwork(network, dir, config)
        }
    }

    fun createParametrisedFsm(network: HybridNetwork, name: String, instance: AutomataInstance): HybridAutomata? {
        if(network.definitions.any({it.name == instance.automata})) {
            // This is currently a really hacky way to do a deep copy, JSON serialize it and then deserialize.
            // Bad for performance, but easy to do. Hopefully can be fixed later?
            val json = mapper.writeValueAsString(network.definitions.first({ it.name == instance.automata }))

            val automata = mapper.readValue<HybridAutomata>(json)
            automata.name = name

            val functionTypes = LinkedHashMap<String, VariableType?>()

            for(function in automata.functions) {
                val inputs = ArrayList(function.inputs)
                inputs.addAll(automata.variables.filter({it.locality == Locality.PARAMETER}).map({ VariableDeclaration(it.name, it.type, ParseTreeLocality.EXTERNAL_INPUT, it.defaultValue) }))
                function.logic.collectVariables(inputs, functionTypes)

                function.returnType = function.logic.getReturnType(functionTypes)
                functionTypes.put(function.name, function.returnType)
            }

            for ((key, value) in instance.parameters) {
                automata.setParameterValue(key, value)
            }

            automata.setDefaultParametrisation()

            return automata
        }

        return null
    }

    fun collectFieldsToLog(network: HybridNetwork, config: Configuration): List<LoggingField> {
        val toLog = ArrayList<LoggingField>()

        if(config.logging.fields == null) {
            // Collect all "outputs" and log them
            for((name, instance) in network.instances) {
                if(network.definitions.any({it.name == instance.automata})) {
                    val definition = network.definitions.first({it.name == instance.automata})

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
                        if(network.definitions.any({it.name == instance.automata})) {
                            val definition = network.definitions.first({it.name == instance.automata})

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