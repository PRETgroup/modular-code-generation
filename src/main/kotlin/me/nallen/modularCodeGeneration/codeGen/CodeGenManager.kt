package me.nallen.modularCodeGeneration.codeGen

import com.fasterxml.jackson.module.kotlin.*
import me.nallen.modularCodeGeneration.codeGen.c.CCodeGenerator
import me.nallen.modularCodeGeneration.hybridAutomata.*
import me.nallen.modularCodeGeneration.hybridAutomata.Locality
import me.nallen.modularCodeGeneration.parseTree.*
import me.nallen.modularCodeGeneration.parseTree.Variable
import java.io.File

typealias ParseTreeLocality = me.nallen.modularCodeGeneration.parseTree.Locality

object CodeGenManager {
    private val mapper = jacksonObjectMapper()
    
    fun generateForNetwork(network: HybridNetwork, language: CodeGenLanguage, dir: String, config: Configuration = Configuration()) {
        val outputDir = File(dir)

        if(outputDir.exists() && !outputDir.isDirectory)
            throw IllegalArgumentException("Desired output directory $dir is not a directory!")

        outputDir.deleteRecursively()
        outputDir.mkdirs()

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
                    outputs.mapTo(toLog) { LoggingField(name, it.name, it.type) }
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

    fun collectSaturationLimits(location: Location, edges: List<Edge>): Map<SaturationPoint, List<String>> {
        val limits = HashMap<SaturationPoint, List<String>>()

        for(edge in edges.filter({it.fromLocation == location.name})) {
            // Find variables that may require saturation
            //   e.g. guard has v >= 100 -- saturate v at 100 falling
            //   e.g. guard has v == 100 -- saturate v at 100 both
            //   e.g. guard has v >= 100 && v <= 120 -- saturate v at 100 falling + 120 rising

            val comparisons = collectComparisonsFromParseTree(edge.guard)

            val saturationPoints = ArrayList<SaturationPoint>()
            for(comparison in comparisons) {
                var variable: String
                var value: ParseTreeItem

                if(comparison.getChildren()[0] is Variable) {
                    variable = comparison.getChildren()[0].generateString()
                    value = comparison.getChildren()[1]
                }
                else if(comparison.getChildren()[1] is Variable) {
                    variable = comparison.getChildren()[1].generateString()
                    value = comparison.getChildren()[0]
                }
                else {
                    continue
                }

                val saturationDirection = if (comparison is LessThan || comparison is GreaterThanOrEqual) {
                    SaturationDirection.FALLING
                }
                else if(comparison is GreaterThan || comparison is LessThanOrEqual) {
                    SaturationDirection.RISING
                }
                else {
                    SaturationDirection.BOTH
                }

                saturationPoints.add(SaturationPoint(variable, value, saturationDirection))
            }

            for(saturationPoint in saturationPoints) {
                val variableName = saturationPoint.variable
                if(location.flow.containsKey(variableName) || location.update.containsKey(variableName))
                    limits.put(saturationPoint, getDependenciesForSaturation(variableName, location))
            }
        }

        return limits
    }

    private fun getDependenciesForSaturation(variable: String, location: Location, updateStack: ArrayList<String> = ArrayList()): List<String> {
        val dependencies = ArrayList<String>()

        if(updateStack.contains(variable))
            return dependencies

        updateStack.add(variable)

        // Check if they are written to by this location
        if(location.flow.containsKey(variable)) {
            return dependencies
        }

        if(location.update.containsKey(variable)) {
            // Written as an update means we need to do more work

            // Check if amenable to saturation (can only use PLUS MINUS, NEGATIVE operators)
            val operations = collectOperationsFromParseTree(location.update[variable]!!)

            if(operations.any({it != "plus" && it != "minus" && it != "negative"})) {
                // Can't saturate
                throw IllegalArgumentException("Unable to saturate update formula ${location.update[variable]!!.generateString()}")
            }

            // Get Dependencies
            val subDependencies = collectVariablesFromParseTree(location.update[variable]!!)
            dependencies.addAll(subDependencies.filter({!dependencies.contains(it)}))

            for(subDependency in subDependencies) {
                dependencies.addAll(getDependenciesForSaturation(subDependency, location, updateStack).filter({!dependencies.contains(it)}))
            }
        }

        return dependencies
    }

    private fun collectVariablesFromParseTree(item: ParseTreeItem): List<String> {
        val variables = ArrayList<String>()

        if(item is Variable) {
            if(!variables.contains(item.name))
                variables.add(item.name)
        }

        for(child in item.getChildren())
            variables.addAll(collectVariablesFromParseTree(child))

        return variables
    }

    private fun collectOperationsFromParseTree(item: ParseTreeItem): List<String> {
        val operands = ArrayList<String>()

        if(item !is Literal && item !is Variable) {
            if(!operands.contains(item.type))
                operands.add(item.type)
        }

        for(child in item.getChildren()) {
            operands.addAll(collectOperationsFromParseTree(child))
        }

        return operands
    }

    private fun collectComparisonsFromParseTree(item: ParseTreeItem): List<ParseTreeItem> {
        val comparisons = ArrayList<ParseTreeItem>()

        if(item is Equal || item is GreaterThan || item is GreaterThanOrEqual || item is LessThan || item is LessThanOrEqual) {
            comparisons.add(item)
        }
        else {
            for(child in item.getChildren()) {
                comparisons.addAll(collectComparisonsFromParseTree(child))
            }
        }

        return comparisons
    }
}

data class SaturationPoint(val variable: String, val value: ParseTreeItem, val direction: SaturationDirection)

enum class SaturationDirection {
    RISING, FALLING, BOTH
}

data class LoggingField(val machine: String, val variable: String, val type: VariableType)

enum class CodeGenLanguage {
    C
}