package me.nallen.modularcodegeneration.description.haml

import com.fasterxml.jackson.annotation.JsonInclude.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.KotlinModule
import me.nallen.modularcodegeneration.codegen.Configuration
import me.nallen.modularcodegeneration.hybridautomata.HybridAutomata
import me.nallen.modularcodegeneration.hybridautomata.HybridItem
import me.nallen.modularcodegeneration.hybridautomata.HybridNetwork
import me.nallen.modularcodegeneration.logging.Logger
import me.nallen.modularcodegeneration.parsetree.Locality
import me.nallen.modularcodegeneration.parsetree.evaluateBoolean

typealias HybridVariable = me.nallen.modularcodegeneration.hybridautomata.Variable
typealias HybridEdge = me.nallen.modularcodegeneration.hybridautomata.Edge
typealias HybridFunction = me.nallen.modularcodegeneration.hybridautomata.FunctionDefinition
typealias HybridInitialisation = me.nallen.modularcodegeneration.hybridautomata.Initialisation
typealias HybridLocality = me.nallen.modularcodegeneration.hybridautomata.Locality

/**
 * An Exporter which is capable of outputting a HybridItem as a HAML file
 */
class Exporter {
    companion object Factory {
        /**
         * Exports the provided item to the specified path in the HAML format.
         *
         * Configuration settings are also included in the output file, where appropriate.
         */
        fun export(item: HybridItem, dir: String, config: Configuration = Configuration()) {

            val schema = Schema(
                    haml = "0.1.2",
                    name = item.name,
                    system = createDefinitionItem(item),
                    codegenConfig = config
            )

            Logger.info("Generating HAML File...")

            val mapper = ObjectMapper(YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES))
            mapper.setSerializationInclusion(Include.NON_NULL)
            mapper.registerModule(KotlinModule())

            val output = mapper.writeValueAsString(schema)

            println(output)
        }
    }
}

/**
 * Creates a DefinitionItem for the given HybridItem
 */
private fun createDefinitionItem(item: HybridItem): DefinitionItem {
    Logger.info("Generating schema for ${item.name}")

    return when(item) {
        is HybridAutomata -> createAutomata(item)
        is HybridNetwork -> createNetwork(item)
        else -> throw IllegalArgumentException("Unexpected type to convert from")
    }
}

/**
 * Creates an Automata for the given HybridAutomata
 */
private fun createAutomata(automata: HybridAutomata): Automata {
    val definition = Automata()

    definition.loadData(automata.variables)

    definition.loadLocations(automata.locations, automata.edges)

    definition.loadFunctions(automata.functions)

    definition.loadInitialisation(automata.init)

    return definition
}

/**
 * Creates a Network for the given HybridNetwork
 */
private fun createNetwork(network: HybridNetwork): Network {
    val definition = Network()

    definition.loadData(network.variables)

    definition.loadDefinitions(network.definitions.values.toList())

    // Instances

    // Mapping

    return definition
}

/**
 * Loads the properties that are used in both Networks and Automata into the definition
 */
private fun DefinitionItem.loadData(variables: List<HybridVariable>) {
    for(variable in variables) {
        val name = variable.name
        val variableDefinition = createVariableDefinition(variable)

        when(variable.locality) {
            HybridLocality.EXTERNAL_INPUT -> {
                if (this.inputs == null)
                    this.inputs = HashMap()
                this.inputs!![name] = variableDefinition
            }
            HybridLocality.EXTERNAL_OUTPUT -> {
                if (this.outputs == null)
                    this.outputs = HashMap()
                this.outputs!![name] = variableDefinition
            }
            HybridLocality.PARAMETER -> {
                if (this.parameters == null)
                    this.parameters = HashMap()
                this.parameters!![name] = variableDefinition
            }
            HybridLocality.INTERNAL -> {}
        }
    }
}

/**
 * Creates a VariableDefinition for the given Hybrid Variable
 */
private fun createVariableDefinition(variable: HybridVariable): VariableDefinition {
    return VariableDefinition(
            type = variable.type.convertToVariableType(),
            default = variable.defaultValue,
            delayableBy = variable.delayableBy
    )
}

/**
 * Converts a Hybrid VariableType to a HAML Variable Type
 */
private fun ParseTreeVariableType.convertToVariableType(): VariableType {
    // A simple mapping between two different enums with the same set of options
    return when(this) {
        ParseTreeVariableType.BOOLEAN -> VariableType.BOOLEAN
        ParseTreeVariableType.REAL -> VariableType.REAL
        ParseTreeVariableType.INTEGER -> VariableType.INTEGER
        ParseTreeVariableType.ANY -> TODO()
    }
}

/**
 * Loads the locations from a Hybrid Automata into an Automata DefinitionItem
 */
private fun Automata.loadLocations(locations: List<HybridLocation>, edges: List<HybridEdge>) {
    if(locations.isNotEmpty() && this.locations == null)
        this.locations = HashMap()

    for(location in locations) {
        this.locations!![location.name] = createLocationDefinition(location, edges)
    }
}

/**
 * Creates a LocationDefinition for the given Hybrid Location
 */
private fun createLocationDefinition(location: HybridLocation, edges: List<HybridEdge>): Location {
    val locationDefinition = Location(
            location.invariant,
            flow = location.flow,
            update = location.update,
            transitions = ArrayList()
    )

    for(edge in edges.filter { it.fromLocation == location.name }) {
        locationDefinition.transitions!!.add(createTransitionDefinition(edge))
    }

    try {
        if (locationDefinition.invariant?.evaluateBoolean() == true)
            locationDefinition.invariant = null
    }
    catch(e: Exception) {}

    if(locationDefinition.flow?.isEmpty() == true)
        locationDefinition.flow = null

    if(locationDefinition.update?.isEmpty() == true)
        locationDefinition.update = null

    return locationDefinition
}

/**
 * Creates a TransitionDefinition for the given Hybrid Edge
 */
private fun createTransitionDefinition(edge: HybridEdge): Transition {
    val transitionDefinition = Transition(
            to = edge.toLocation,
            guard = edge.guard,
            update = edge.update
    )

    try {
        if (transitionDefinition.guard?.evaluateBoolean() == true)
            transitionDefinition.guard = null
    }
    catch(e: Exception) {}

    if(transitionDefinition.update?.isEmpty() == true)
        transitionDefinition.update = null

    return transitionDefinition
}

/**
 * Loads the functions from a Hybrid Automata into an Automata DefinitionItem
 */
private fun Automata.loadFunctions(functions: List<HybridFunction>) {
    if(functions.isNotEmpty() && this.functions == null)
        this.functions = HashMap()

    for(function in functions) {
        this.functions!![function.name] = createFunctionDefinition(function)
    }
}

/**
 * Creates a TransitionDefinition for the given Hybrid Edge
 */
private fun createFunctionDefinition(function: HybridFunction): Function {
    val functionDefinition = Function(
            inputs = null,
            logic = function.logic
    )

    if(function.inputs.filter { it.locality == Locality.EXTERNAL_INPUT }.isNotEmpty())
        functionDefinition.inputs = HashMap()

    for(input in function.inputs.filter { it.locality == Locality.EXTERNAL_INPUT }) {
        val name = input.name

        functionDefinition.inputs!![name] = VariableDefinition(
                type = input.type.convertToVariableType(),
                default = input.defaultValue
        )
    }

    return functionDefinition
}

/**
 * Loads the initialisation from a Hybrid Automata into an Automata DefinitionItem
 */
private fun Automata.loadInitialisation(init: HybridInitialisation) {
    this.initialisation = Initialisation(
            state = init.state,
            valuations = init.valuations
    )

    if(this.initialisation!!.valuations!!.isEmpty()) {
        this.initialisation!!.valuations = null
    }
}

/**
 * Loads the definitions from a HybridNetwork into a Network DefinitionItem
 */
private fun Network.loadDefinitions(definitions: List<HybridItem>) {
    for(definition in definitions) {
        this.definitions[definition.name] = createDefinitionItem(definition)
    }
}