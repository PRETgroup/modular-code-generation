package me.nallen.modularCodeGeneration.description

import com.fasterxml.jackson.annotation.JsonCreator
import me.nallen.modularCodeGeneration.parseTree.ParseTreeItem
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.parseTree.Program

/**
 * The root object of the HAML Document
 */
data class Schema(
        // The name of this Hybrid Network
        var name: String,

        // A set of definitions of Hybrid Automata that can be instantiated
        var definitions: Map<String, Definition>,

        // A set of instances of previously defined Hybrid Automata
        var instances: Map<String, Instance>,

        // A set of mappings that determine the value of each input of each Instance
        var mappings: Map<String, ParseTreeItem>?,

        // A list of settings available for the default code generation logic in this tool
        var codegenConfig: Configuration?
)

/**
 * The object that captures a Hybrid Automata and its logic
 */
data class Definition(
        // The variables that this Hybrid Automata accepts as inputs
        var inputs: Map<String, VariableDefinition>?,

        // The variables that this Hybrid Automata emits as outputs
        var outputs: Map<String, VariableDefinition>?,

        // The parameters that are available for configuration of this Hybrid Automata
        var parameters: Map<String, VariableDefinition>?,

        // The locations that exist inside this Hybrid Automata
        var locations: Map<String, Location>?,

        // A set of functions that exist inside this Hybrid Automata
        var functions: Map<String, Function>?,

        // Sets the initialisation options for the Hybrid Automata (location, variable states, etc.)
        var initialisation: Initialisation
)

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
}

/**
 * An enum that represents the type of a variable
 */
enum class VariableType {
    BOOLEAN, REAL
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
        var flow: Map<String, ParseTreeItem>?,

        // A set of discrete operations that are done while inside this location
        var update: Map<String, ParseTreeItem>?,

        // A set of transitions that exist out of this location
        var transitions: List<Transition>?
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
        var update: Map<String, ParseTreeItem>?
)

/**
 * A function that exists within a Hybrid Automata and can be invoked. The return type (if any) is automatically
 * generated from the logic
 */
data class Function(
        // The set of inputs that this function accepts
        var inputs: Map<String, VariableDefinition>?,

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
        var valuations: Map<String, ParseTreeItem>?
)

/**
 * An instantiation of a Hybrid Automata Definition
 */
data class Instance(
        // The previously declared definition that this instance instantiates
        var type: String,

        // The values of any parameters inside the previous declaration. Any parameters which do not have an entry here
        // will inherit their default value (if any)
        var parameters: Map<String, ParseTreeItem>? = null
) {
    companion object Factory {
        @JsonCreator @JvmStatic
        fun create(input: String) = Instance(input)
    }
}