package me.nallen.modularCodeGeneration.description

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.hybridAutomata.*
import me.nallen.modularCodeGeneration.parseTree.Literal
import me.nallen.modularCodeGeneration.parseTree.ParseTreeItem
import me.nallen.modularCodeGeneration.parseTree.VariableDeclaration
import java.io.File
import java.io.IOException

typealias ParseTreeVariableType = me.nallen.modularCodeGeneration.parseTree.VariableType
typealias ParseTreeLocality = me.nallen.modularCodeGeneration.parseTree.Locality
typealias HybridLocation = me.nallen.modularCodeGeneration.hybridAutomata.Location

class Importer {
    companion object Factory {
        fun import(path: String): Pair<HybridNetwork, Configuration> {
            val parsedFile = parseIncludes(path)

            val mapper = ObjectMapper(YAMLFactory())
            mapper.registerModule(KotlinModule())

            val schema = mapper.readValue(parsedFile, Schema::class.java)

            val network = HybridNetwork()

            network.name = schema.name

            network.importAutomata(schema.definitions)

            network.importInstances(schema.instances)

            network.importMappings(schema.mappings)

            val config = schema.codegenConfig ?: Configuration()

            return Pair(network, config)
        }

        private fun parseIncludes(path: String): String {
            val file = File(path)

            if(!file.exists() || !file.isFile)
                throw Exception("Whoops")

            val builder = StringBuilder()
            val lines = file.readLines()

            val includeRegex = Regex("!include\\s+([^\\s]+)")
            val indentRegex = Regex("^([\\s]*)[^\\s]+")

            for(line in lines) {
                val match = includeRegex.find(line)
                if(match != null) {
                    val includedFile = File(file.parentFile.absolutePath, match.groupValues[1]).absolutePath
                    var indent = indentRegex.find(line)?.groupValues?.get(1) ?: ""

                    val pretext = line.substring(0 until match.range.first)

                    if(pretext.isNotBlank()) {
                        builder.appendln(line.substring(0 until match.range.first))
                        indent += "  "
                    }

                    builder.appendln(parseIncludes(includedFile).trim().prependIndent(indent))
                }
                else {
                    builder.appendln(line)
                }
            }

            return builder.toString()
        }
    }
}

private fun HybridNetwork.importAutomata(definitions: Map<String, Definition>) {
    for((name, definition) in definitions) {
        val automata = HybridAutomata(name)

        automata.loadVariables(definition.inputs, Locality.EXTERNAL_INPUT)

        automata.loadVariables(definition.outputs, Locality.EXTERNAL_OUTPUT)

        automata.loadVariables(definition.parameters, Locality.PARAMETER)

        automata.loadLocations(definition.locations)

        automata.loadInitialisation(definition.initialisation)

        automata.loadFunctions(definition.functions)

        this.addDefinition(automata)
    }
}

private fun HybridAutomata.loadLocations(locations: Map<String, Location>?) {
    if(locations != null) {
        for((key, value) in locations) {
            this.loadLocation(key, value)
        }
    }
}

private fun HybridAutomata.loadLocation(name: String, location: Location?) {
    val hybridLocation = HybridLocation(name)

    hybridLocation.invariant = location?.invariant ?: Literal("true")

    hybridLocation.flow.loadParseTreeItems(location?.flow)

    hybridLocation.update.loadParseTreeItems(location?.update)

    this.loadTransitions(name, location?.transitions)

    this.addLocation(hybridLocation)
}

private fun HybridAutomata.loadTransitions(from: String, transitions: List<Transition>?) {
    if(transitions != null) {
        for(transition in transitions) {
            this.loadTransition(from, transition)
        }
    }
}

private fun HybridAutomata.loadTransition(from: String, transition: Transition) {
    val edge = Edge(from, transition.to)

    edge.guard = transition.guard ?: Literal("true")
    edge.update.loadParseTreeItems(transition.update)

    this.addEdge(edge)
}

private fun HybridAutomata.loadInitialisation(init: Initialisation) {
    this.init.state = init.state

    this.init.valuations.loadParseTreeItems(init.valuations)
}

private fun HybridAutomata.loadFunctions(functions: Map<String, Function>?) {
    if(functions != null) {
        val functionTypes = LinkedHashMap<String, ParseTreeVariableType?>()

        for((name, function) in functions) {
            this.loadFunction(name, function, functionTypes)
        }
    }
}

private fun HybridAutomata.loadFunction(name: String, function: Function, existingFunctionTypes: HashMap<String, ParseTreeVariableType?>) {
    val inputs = ArrayList<VariableDeclaration>()
    if(function.inputs != null) {
        for(input in function.inputs!!) {
            inputs.add(VariableDeclaration(input.key, input.value.type.convertToParseTreeType(), ParseTreeLocality.EXTERNAL_INPUT, input.value.default))
        }
    }

    function.logic.collectVariables(inputs, existingFunctionTypes)

    val func = FunctionDefinition(name, function.logic, inputs)

    func.returnType = function.logic.getReturnType(existingFunctionTypes)
    existingFunctionTypes.put(func.name, func.returnType)

    this.functions.add(func)
}

private fun VariableType.convertToParseTreeType(): ParseTreeVariableType {
    return when(this) {
        VariableType.BOOLEAN -> ParseTreeVariableType.BOOLEAN
        VariableType.REAL -> ParseTreeVariableType.REAL
    }
}

private fun HybridNetwork.importInstances(instances: Map<String, Instance>) {
    for((name, instance) in instances) {
        val automataInstance = AutomataInstance(instance.type)

        automataInstance.parameters.loadParseTreeItems(instance.parameters)

        this.addInstance(name, automataInstance)
    }
}

private fun HybridNetwork.importMappings(mappings: Map<String, ParseTreeItem>?) {
    if(mappings != null) {
        for((to, from) in mappings) {
            if(to.contains(".")) {
                val toPair = AutomataVariablePair(to.substringBeforeLast("."), to.substringAfterLast("."))

                this.ioMapping.put(toPair, from)
            }
            else {
                throw IOException("Invalid IO Mapping provided for $from -> $to")
            }
        }
    }
}

private fun MutableMap<String, ParseTreeItem>.loadParseTreeItems(items: Map<String, ParseTreeItem>?) {
    if(items != null) {
        for((key, value) in items) {
            this.put(key, value)
        }
    }
}

private fun HybridAutomata.loadVariables(variables: Map<String, VariableDefinition>?, type: Locality) {
    if(variables != null) {
        for((key, value) in variables) {
            this.loadVariable(key, value, type)
        }
    }
}

private fun HybridAutomata.loadVariable(name: String, value: VariableDefinition, type: Locality) {
    if(value.type == VariableType.REAL) {
        this.addContinuousVariable(name, type, value.default, value.delayableBy)
    }
    else if(value.type == VariableType.BOOLEAN) {
        this.addEvent(name, type, value.delayableBy)
    }
}