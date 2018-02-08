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
import java.util.*

typealias ParseTreeVariableType = me.nallen.modularCodeGeneration.parseTree.VariableType
typealias ParseTreeLocality = me.nallen.modularCodeGeneration.parseTree.Locality
typealias HybridLocation = me.nallen.modularCodeGeneration.hybridAutomata.Location

/**
 * An Importer which is capable of reading in a HAML Document specification and creating the associated Hybrid Network
 * as described in the document.
 */
class Importer {
    companion object Factory {
        /**
         * Imports the HAML document at the specified path and converts it to a Hybrid Network.
         *
         * The configuration settings stored in the HAML documents are also parsed and returned as a separate
         * Configuration object.
         */
        fun import(path: String): Pair<HybridItem, Configuration> {
            // Firstly we want to handle any includes that may exist in the file
            val parsedFile = parseIncludes(path)

            // Now we want to read the file as a YAML file...
            val mapper = ObjectMapper(YAMLFactory())
            mapper.registerModule(KotlinModule())

            // ... and convert it into a Schema object
            val schema = mapper.readValue(parsedFile, Schema::class.java)

            val item: HybridItem = when(schema.system) {
                is Network -> {
                    createHybridNetwork(schema.name, schema.system)
                }
                is Automata -> {
                    createHybridAutomata(schema.name, schema.system)
                }
            }

            // Create the configuration
            val config = schema.codegenConfig ?: Configuration()

            return Pair(item, config)
        }

        /**
         * Parses the file at the provided path for any include statements in the YAML document.
         *
         * Include statements consist of a "!include" followed by the path to the included file.
         *
         * Once all includes have been parsed, the final YAML document string will be returned.
         */
        private fun parseIncludes(path: String): String {
            val file = File(path)

            // Try to open the file
            if(!file.exists() || !file.isFile)
                throw Exception("Whoops")

            val builder = StringBuilder()
            val lines = file.readLines()

            // A pair of regex for detecting include statements
            val includeRegex = Regex("!include\\s+([^\\s]+)")
            val indentRegex = Regex("^([\\s]*)[^\\s]+")

            // Iterate over every line
            for(line in lines) {
                // Check if we can find an include statement on this line
                val match = includeRegex.find(line)
                if(match != null) {
                    // We found one! Now time to parse it
                    // Firstly we want to open the included file
                    val includedFile = File(file.parentFile.absolutePath, match.groupValues[1]).absolutePath

                    // Now we need to figure out how much of an indent there was at the start of this line, since
                    // YAML is whitespace sensitive for indentation
                    var indent = indentRegex.find(line)?.groupValues?.get(1) ?: ""

                    // We include the line until the include statement as is
                    val pretext = line.substring(0 until match.range.first)

                    // If there was any text before the include statement then we want to append that, and now we need
                    // to indent one level further (2 spaces per tab in YAML)
                    if(pretext.isNotBlank()) {
                        builder.appendln(pretext)
                        indent += "  "
                    }

                    // Add the included file (parsing any includes it may have first) and apply the indent
                    builder.appendln(parseIncludes(includedFile).trim().prependIndent(indent))
                }
                else {
                    // No include statement, so let's just append the line
                    builder.appendln(line)
                }
            }

            // All done! Can return the final string
            return builder.toString()
        }
    }
}

private fun createHybridAutomata(name: String, definition: Automata): HybridAutomata {
    // Create the automata
    val automata = HybridAutomata(name)

    // Load all inputs
    automata.loadVariables(definition.inputs, Locality.EXTERNAL_INPUT)

    // And all outputs
    automata.loadVariables(definition.outputs, Locality.EXTERNAL_OUTPUT)

    // And all parameters
    automata.loadVariables(definition.parameters, Locality.PARAMETER)

    // Add the locations (transitions are within locations)
    automata.loadLocations(definition.locations)

    // Set the initialisation
    automata.loadInitialisation(definition.initialisation)

    // And then any custom functions that it may contain
    automata.loadFunctions(definition.functions)

    return automata
}

private fun createHybridNetwork(name: String, definition: Network): HybridNetwork {
    // Now let's create the Hybrid Network
    val network = HybridNetwork()

    // Import the name
    network.name = name

    // Import the definitions
    network.importItems(definition.definitions)

    // Import the instances
    network.importInstances(definition.instances)

    // Import the mappings
    network.importMappings(definition.mappings)

    return network
}

/**
 * Imports all Definitions in the HAML spec into their respective HybridAutomata representations
 */
private fun HybridNetwork.importItems(definitions: Map<String, DefinitionItem>) {
    // We want to add every definition, so iterate!
    for((name, definition) in definitions) {
        val uuid = when(definition) {
            is Automata -> this.loadDefinition(name, definition)
            is Network -> UUID.randomUUID()
        }

        this.instantiates.put(UUID.randomUUID(), AutomataInstantiate(uuid, name))
    }
}

private fun HybridNetwork.loadDefinition(name: String, definition: Automata): UUID {
    val automata = createHybridAutomata(name, definition)

    val definitionUUID = UUID.randomUUID()
    this.definitions.put(definitionUUID, automata)

    return definitionUUID
}

/**
 * Imports each Location in a HAML Definition into the given HybridAutomata instance
 */
private fun HybridAutomata.loadLocations(locations: Map<String, Location>?) {
    // For each location that exists
    if(locations != null) {
        for((name, location) in locations) {
            // Create an associated HybridLocation
            val hybridLocation = HybridLocation(name)

            // Copy over the Invariant (defaults to "true")
            hybridLocation.invariant = location.invariant ?: Literal("true")

            // Load the Flow
            hybridLocation.flow.loadParseTreeItems(location.flow)

            // And load the Updates
            hybridLocation.update.loadParseTreeItems(location.update)

            // Add any transitions
            this.loadTransitions(name, location.transitions)

            this.addLocation(hybridLocation)
        }
    }
}

/**
 * Imports each Transition in a HAML Location into the given HybridAutomata instance
 */
private fun HybridAutomata.loadTransitions(from: String, transitions: List<Transition>?) {
    // For each transition that exists
    if(transitions != null) {
        for(transition in transitions) {
            // Create an associated Edge
            val edge = Edge(from, transition.to)

            // Copy over the Guard (defaults to "true")
            edge.guard = transition.guard ?: Literal("true")

            // And then load the Updates
            edge.update.loadParseTreeItems(transition.update)

            this.addEdge(edge)
        }
    }
}

/**
 * Imports a HAML Initialisation spec into the given HybridAutomata instance
 */
private fun HybridAutomata.loadInitialisation(init: Initialisation?) {
    if(init != null) {
        // Initial state is 1:1 mapping
        this.init.state = init.state

        // And load all the initial valuations
        this.init.valuations.loadParseTreeItems(init.valuations)
    }
}

/**
 * Imports a set of HAML Function definitions into the given HybridAutomata instance
 */
private fun HybridAutomata.loadFunctions(functions: Map<String, Function>?) {
    // We need to keep track of functions we know about and their return types
    val existingFunctionTypes = LinkedHashMap<String, ParseTreeVariableType?>()

    // For each function that exists
    if(functions != null) {
        for((name, function) in functions) {
            // First up, let's detect all the inputs to the function
            val inputs = ArrayList<VariableDeclaration>()
            if(function.inputs != null) {
                // We need to iterate over every input...
                for(input in function.inputs!!) {
                    // ... and create a VariableDeclaration that matches what we want
                    inputs.add(VariableDeclaration(input.key, input.value.type.convertToParseTreeType(), ParseTreeLocality.EXTERNAL_INPUT, input.value.default))
                }
            }

            // Next let's find any internal variables inside the function that we'll need to instantiate
            function.logic.collectVariables(inputs, existingFunctionTypes)

            // Now we create the FunctionDefinition
            val func = FunctionDefinition(name, function.logic, inputs)

            // Finally let's find out what return type the function should be
            func.returnType = function.logic.getReturnType(existingFunctionTypes)
            // And then add it to the list of known types for future parsing
            existingFunctionTypes[func.name] = func.returnType

            this.functions.add(func)
        }
    }
}

/**
 * Converts a HAML Variable Type to a Hybrid VariableType
 */
private fun VariableType.convertToParseTreeType(): ParseTreeVariableType {
    // A simple mapping between two different enums with the same set of options
    return when(this) {
        VariableType.BOOLEAN -> ParseTreeVariableType.BOOLEAN
        VariableType.REAL -> ParseTreeVariableType.REAL
    }
}

/**
 * Imports a set of HAML Instances into the given HybridNetwork instance
 */
private fun HybridNetwork.importInstances(instances: Map<String, Instance>) {
    // For each instance that exists
    for((name, instance) in instances) {
        val instantiateId = this.instantiates.filter { it.value.name.equals(instance.type) }.keys.firstOrNull()
        if(instantiateId != null) {
            // We create the associated instance
            val automataInstance = AutomataInstance(instantiateId)

            // And then add all the parameters that should be set on it
            automataInstance.parameters.loadParseTreeItems(instance.parameters)

            // Remembering that the instance name is the key in the Map they get stored in
            this.instances[name] = automataInstance
        }
    }
}

/**
 * Imports a set of HAML Mappings into the given HybridNetwork instance
 */
private fun HybridNetwork.importMappings(mappings: Map<String, ParseTreeItem>?) {
    // For each mapping that exists
    if(mappings != null) {
        for((to, from) in mappings) {
            // We need to check that the "to" part contains a dot, which is used to separate the instance from the
            // input variable name
            if(to.contains(".")) {
                // Split the "to" field up into its Automata and Variable pairs
                val toPair = AutomataVariablePair(to.substringBeforeLast("."), to.substringAfterLast("."))

                // And add it!
                this.ioMapping[toPair] = from
            }
            else {
                // Invalid "to" given
                throw IOException("Invalid IO Mapping provided for $from -> $to")
            }
        }
    }
}

/**
 * Adds a set of ParseTreeItems to the given and existing set of ParseTreeItems.
 *
 * Note that this method will overwrite any existing values which share the same key as one being added.
 */
private fun MutableMap<String, ParseTreeItem>.loadParseTreeItems(items: Map<String, ParseTreeItem>?) {
    // For each item that we have
    if(items != null) {
        for((key, value) in items) {
            // Simply add it to the map
            this[key] = value
        }
    }
}

/**
 * Imports a set of HAML Variables with a given locality into the given HybridAutomata
 */
private fun HybridAutomata.loadVariables(variables: Map<String, VariableDefinition>?, type: Locality) {
    // Iterate over every variable we were given
    if(variables != null) {
        for((name, value) in variables) {
            // Check the type of the variable
            if(value.type == VariableType.REAL) {
                // Real variables go to Continuous Variables
                this.addContinuousVariable(name, type, value.default, value.delayableBy)
            }
            else if(value.type == VariableType.BOOLEAN) {
                // Booleans become Events
                this.addEvent(name, type, value.delayableBy)
            }
        }
    }
}