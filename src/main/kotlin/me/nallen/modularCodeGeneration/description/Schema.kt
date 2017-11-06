package me.nallen.modularCodeGeneration.description

import me.nallen.modularCodeGeneration.parseTree.ParseTreeItem

data class Schema(
        var definitions: Map<String, Definition>,
        var instances: Map<String, Instance>,
        var mappings: Map<String, String>?
)

data class Definition(
        var inputs: Map<String, VariableType>?,
        var outputs: Map<String, VariableType>?,
        var parameters: Map<String, VariableType>?,
        var locations: Map<String, Location>?,
        var initialisation: Initialisation
)

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

data class Initialisation(
        var state: String,
        var valuations: Map<String, ParseTreeItem>?
)

data class Instance(
        var type: String,
        var parameters: Map<String, ParseTreeItem>?
)