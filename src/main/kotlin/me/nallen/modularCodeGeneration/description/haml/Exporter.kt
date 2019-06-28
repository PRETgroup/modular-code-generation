package me.nallen.modularcodegeneration.description.haml

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import me.nallen.modularcodegeneration.codegen.Configuration
import me.nallen.modularcodegeneration.hybridautomata.HybridAutomata
import me.nallen.modularcodegeneration.hybridautomata.HybridItem
import me.nallen.modularcodegeneration.hybridautomata.HybridNetwork
import me.nallen.modularcodegeneration.logging.Logger

typealias HamlLocation = me.nallen.modularcodegeneration.hybridautomata.Location
typealias HamlVariable = me.nallen.modularcodegeneration.hybridautomata.Variable
typealias HamlLocality = me.nallen.modularcodegeneration.hybridautomata.Locality

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

            val mapper = ObjectMapper(YAMLFactory())
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

    definition.loadLocations(automata.locations)

    //definition.loadFunctions(automata.functions)

    //definition.loadInitialisation(automata.init)

    return definition
}

/**
 * Creates a Network for the given HybridNetwork
 */
private fun createNetwork(network: HybridNetwork): Network {
    val definition = Network()

    definition.loadData(network.variables)

    return definition
}

/**
 * Loads the properties that are used in both Networks and Automata into the definition
 */
private fun DefinitionItem.loadData(variables: List<HamlVariable>) {
    for(variable in variables) {
        val name = variable.name
        val variableDefinition = createVariableDefinition(variable)

        when(variable.locality) {
            HamlLocality.EXTERNAL_INPUT -> {
                if (this.inputs == null)
                    this.inputs = HashMap()
                this.inputs!![name] = variableDefinition
            }
            HamlLocality.EXTERNAL_OUTPUT -> {
                if (this.outputs == null)
                    this.outputs = HashMap()
                this.outputs!![name] = variableDefinition
            }
            HamlLocality.INTERNAL -> {
                if (this.parameters == null)
                    this.parameters = HashMap()
                this.parameters!![name] = variableDefinition
            }
            HamlLocality.PARAMETER -> {}
        }
    }
}

/**
 * Creates a VariableDefinition for the given Hybrid Variable
 */
private fun createVariableDefinition(variable: HamlVariable): VariableDefinition {
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
private fun Automata.loadLocations(locations: List<HamlLocation>) {
    if(locations.isNotEmpty() && this.locations == null)
        this.locations = HashMap()

    for(location in locations) {

    }
}