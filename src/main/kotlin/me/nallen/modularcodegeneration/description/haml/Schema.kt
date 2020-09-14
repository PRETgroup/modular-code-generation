package me.nallen.modularcodegeneration.description.haml

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import me.nallen.modularcodegeneration.parsetree.ParseTreeItem
import me.nallen.modularcodegeneration.codegen.Configuration
import me.nallen.modularcodegeneration.parsetree.Program

/**
 * The root object of the HAML Document
 */
data class Schema(
        // The name of this HAML Document
        var name: String,

        // The main item that describes the system
        val system: DefinitionItem,

        // A list of settings available for the default code generation logic in this tool
        var codegenConfig: Configuration? = null
)

/**
 * The object that captures a Hybrid Network, including mappings and instantiations.
 * A Network can instantiate further networks inside of it, to create a hierarchical structure.
 */
class Network: DefinitionItem() {
    // A set of definitions of Hybrid Automata or Hybrid Networks that can be instantiated
    var definitions: LinkedHashMap<String, DefinitionItem>? = null

    // A set of instances of previously defined Hybrid Automata or Hybrid Networks
    var instances: LinkedHashMap<String, Instance>? = null

    // A set of mappings that determine the value of each input of each Instance
    var mappings: LinkedHashMap<String, ParseTreeItem>? = null
}

/**
 * The object that captures a Hybrid Automata and its logic
 */
class Automata: DefinitionItem() {
    // The locations that exist inside this Hybrid Automata
    var locations: LinkedHashMap<String, Location>? = null

    // Sets the initialisation options for the Hybrid Automata (location, variable states, etc.)
    var initialisation: Initialisation? = null
}

/**
 * An abstract (sealed) class to support Polymorphism of Networks and Automata
 */
sealed class DefinitionItem {
    // The variables that this Hybrid Item accepts as inputs
    var inputs: LinkedHashMap<String, VariableDefinition>? = null

    // The variables that this Hybrid Item emits as outputs
    var outputs: LinkedHashMap<String, VariableDefinition>? = null

    // The parameters that are available for configuration of this Hybrid Item
    var parameters: LinkedHashMap<String, VariableDefinition>? = null

    // A set of functions that exist inside this Hybrid Item
    var functions: LinkedHashMap<String, Function>? = null

    companion object Factory {
        // Method for creating from a String (used in JSON parsing)
        @JsonCreator @JvmStatic
        fun generate(node: JsonNode): DefinitionItem? {
            // When creating, it needs to be an object
            if(node.isObject) {
                val mapper = jacksonObjectMapper()

                // Then we need to try guess what type we're actually trying to deserialize, this is done by checking
                // if the object has a field named "instances"
                return if(node.has("instances"))
                    mapper.treeToValue(node, Network::class.java)
                else {
                    mapper.treeToValue(node, Automata::class.java)
                }
            }
            return null
        }
    }
}

/**
 * Information about a variable that exists within a Hybrid Automata
 */
data class VariableDefinition(
        // The type of the variable
        var type: VariableType,

        // The default value for the variable
        var default: ParseTreeItem? = null,

        // An amount of time that this variable could possibly be delayed by. Used in code generation
        var delayableBy: ParseTreeItem? = null
) {
    companion object Factory {
        @JsonCreator @JvmStatic
        fun create(input: String) = VariableDefinition(VariableType.valueOf(input))
    }

    @JsonValue
    fun serialise(): Any {
        if(default == null && delayableBy == null) {
            return type
        }

        return mapOf(
                "type" to type,
                "default" to default,
                "delayableBy" to delayableBy
        )
    }
}

/**
 * An enum that represents the type of a variable
 */
enum class VariableType {
    BOOLEAN, REAL, INTEGER
}

/**
 * A single location within a Hybrid Automata
 */
data class Location(
        // The invariant that exists on this location. For control to remain in this location the invariant must hold
        // true
        var invariant: ParseTreeItem?,

        // The set of flow constraints that exist for each ODE, these constraints will transform the values of the
        // variables while control remains in this location
        var flow: LinkedHashMap<String, ParseTreeItem>?,

        // A set of discrete operations that are done while inside this location
        var update: LinkedHashMap<String, ParseTreeItem>?,

        // A set of transitions that exist out of this location
        var transitions: ArrayList<Transition>?
)

/**
 * A transition to another location within a Hybrid Automata
 */
data class Transition(
        // The name of the destination location for this transition
        var to: String,

        // The guard that protects when this transition is "active" and can be taken
        var guard: ParseTreeItem?,

        // A set of discrete operations that are done when this transition is taken
        var update: LinkedHashMap<String, ParseTreeItem>?
)

/**
 * A function that exists within a Hybrid Automata and can be invoked. The return type (if any) is automatically
 * generated from the logic
 */
data class Function(
        // The set of inputs that this function accepts
        var inputs: LinkedHashMap<String, VariableDefinition>?,

        // The code that this function will perform when invoked
        var logic: Program
)

/**
 * Initialisation of a Hybrid Automata, including its initial state and variable valuations
 */
data class Initialisation(
        // The initial state that the Hybrid Automata will start in
        var state: String,

        // The initial set of valuations for variables in the Hybrid Automata
        var valuations: LinkedHashMap<String, ParseTreeItem>?
)

/**
 * An instantiation of a Hybrid Automata Definition
 */
data class Instance(
        // The previously declared definition that this instantiate instantiates
        var type: String,

        // The values of any parameters inside the previous declaration. Any parameters which do not have an entry here
        // will inherit their default value (if any)
        var parameters: LinkedHashMap<String, ParseTreeItem>? = null
) {
    companion object Factory {
        @JsonCreator @JvmStatic
        fun create(input: String) = Instance(input)
    }

    @JsonValue
    fun serialise(): Any {
        if(parameters == null) {
            return type
        }

        return mapOf(
                "type" to type,
                "parameters" to parameters
        )
    }
}