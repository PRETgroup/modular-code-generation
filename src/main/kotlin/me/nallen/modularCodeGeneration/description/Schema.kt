package me.nallen.modularCodeGeneration.description

import com.fasterxml.jackson.annotation.JsonCreator
import me.nallen.modularCodeGeneration.parseTree.ParseTreeItem
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.parseTree.Program

data class Schema(
        var name: String,
        var definitions: Map<String, Definition>,
        var instances: Map<String, Instance>,
        var mappings: Map<String, String>?,
        var codegenConfig: Configuration?
)

data class Definition(
        var inputs: Map<String, VariableDefinition>?,
        var outputs: Map<String, VariableDefinition>?,
        var parameters: Map<String, VariableDefinition>?,
        var locations: Map<String, Location>?,
        var functions: Map<String, Function>?,
        var initialisation: Initialisation
)

data class VariableDefinition(
        var type: VariableType,
        var default: ParseTreeItem? = null
) {
    companion object Factory {
        @JsonCreator @JvmStatic
        fun create(input: String) = VariableDefinition(VariableType.valueOf(input))
    }
}

enum class VariableType {
    BOOLEAN, REAL
}

data class Location(
        var invariant: ParseTreeItem?,
        var flow: Map<String, ParseTreeItem>?,
        var update: Map<String, ParseTreeItem>?,
        var transitions: List<Transition>?
)

data class Transition(
        var to: String,
        var guard: ParseTreeItem?,
        var update: Map<String, ParseTreeItem>?
)

data class Function(
        var inputs: Map<String, VariableDefinition>?,
        var logic: Program
)

data class Initialisation(
        var state: String,
        var valuations: Map<String, ParseTreeItem>?
)

data class Instance(
        var type: String,
        var parameters: Map<String, ParseTreeItem>?
)