package me.nallen.modularCodeGeneration.hybridAutomata

import me.nallen.modularCodeGeneration.parseTree.*
import me.nallen.modularCodeGeneration.parseTree.Variable as ParseTreeVariable

/**
 * Created by nall426 on 31/05/2017.
 */

data class HybridAutomata(
        var name: String = "HA",

        val locations: ArrayList<Location> = ArrayList<Location>(),
        val edges: ArrayList<Edge> = ArrayList<Edge>(),
        var init: Initialisation = Initialisation(""),

        val functions: ArrayList<FunctionDefinition> = ArrayList<FunctionDefinition>(),

        val continuousVariables: ArrayList<Variable> = ArrayList<Variable>(),
        val events: ArrayList<Variable> = ArrayList<Variable>()
) {
    fun addLocation(location: Location): HybridAutomata {
        /*if(locations.any({it.name == location.name}))
            throw IllegalArgumentException("Location with name ${location.name} already exists!")*/

        // Check for any continuous variables
        checkParseTreeForNewContinuousVariable(location.invariant)

        for((key, value) in location.flow) {
            addContinuousVariable(key)
            checkParseTreeForNewContinuousVariable(value)
        }

        for((key, value) in location.update) {
            addContinuousVariable(key)
            checkParseTreeForNewContinuousVariable(value)
        }

        locations.add(location)

        return this
    }

    fun addEdge(edge: Edge): HybridAutomata {
        /*if(!locations.any({ it.name == edge.fromLocation }))
            throw IllegalArgumentException("Location with name ${edge.fromLocation} does not exist!")

        if(!locations.any({ it.name == edge.toLocation }))
            throw IllegalArgumentException("Location with name ${edge.toLocation} does not exist!")

        for((fromLocation, toLocation, guard) in edges) {
            if(fromLocation == edge.fromLocation
                    && toLocation == edge.toLocation && guard == edge.guard) {
                //TODO: Should become a WARN once logging implemented
                throw IllegalArgumentException("Edge with same (from, to, guard) " +
                        "($fromLocation, $toLocation, $guard pairing already exists!")

                //TODO: Should be updated to merge updates if different
            }
        }*/

        // Check for any continuous variables
        checkParseTreeForNewContinuousVariable(edge.guard)

        for((key, value) in edge.update) {
            addContinuousVariable(key)
            checkParseTreeForNewContinuousVariable(value)
        }

        // Check for any events
        checkParseTreeForNewEvent(edge.inEvents, Locality.EXTERNAL_INPUT)

        for(event in edge.outEvents) {
            addEvent(event, Locality.EXTERNAL_OUTPUT)
        }

        edges.add(edge)

        return this
    }

    fun addContinuousVariable(item: String, locality: Locality = Locality.INTERNAL, default: ParseTreeItem? = null): HybridAutomata {
        if(!continuousVariables.any({it.name == item})) {
            continuousVariables.add(Variable(item, locality, default))

            if(default != null)
                checkParseTreeForNewContinuousVariable(default)
        }

        return this
    }

    fun addEvent(item: String, locality: Locality = Locality.INTERNAL): HybridAutomata {
        if(!events.any({it.name == item})) {
            events.add(Variable(item, locality))
        }

        return this
    }

    /* Private Methods */

    private fun checkParseTreeForNewContinuousVariable(item: ParseTreeItem, locality: Locality = Locality.INTERNAL) {
        if(item is ParseTreeVariable) {
            addContinuousVariable(item.name, locality)
        }

        for(child in item.getChildren()) {
            checkParseTreeForNewContinuousVariable(child)
        }
    }

    private fun checkParseTreeForNewEvent(item: ParseTreeItem, locality: Locality = Locality.INTERNAL) {
        if(item is ParseTreeVariable) {
            addEvent(item.name, locality)
        }

        for(child in item.getChildren()) {
            checkParseTreeForNewEvent(child, locality)
        }
    }
}

data class Location(
        var name: String,
        var invariant: ParseTreeItem = Literal("true"),
        var flow: MutableMap<String, ParseTreeItem> = LinkedHashMap<String, ParseTreeItem>(),
        var update: MutableMap<String, ParseTreeItem> = LinkedHashMap<String, ParseTreeItem>()
)

data class Edge(
        var fromLocation: String,
        var toLocation: String,
        var guard: ParseTreeItem = Literal("true"),
        var update: MutableMap<String, ParseTreeItem> = LinkedHashMap<String, ParseTreeItem>(),
        var inEvents: ParseTreeItem = Literal("true"),
        var outEvents: List<String> = ArrayList<String>()
)

data class Initialisation(
        var state: String,
        var valuations: MutableMap<String, ParseTreeItem> = LinkedHashMap<String, ParseTreeItem>()
)

data class Variable(
        var name: String,
        var locality: Locality,
        var defaultValue: ParseTreeItem? = null
)

enum class Locality {
    INTERNAL, EXTERNAL_INPUT, EXTERNAL_OUTPUT, PARAMETER
}

data class FunctionDefinition(
        var name: String,
        var logic: Program,
        var inputs: ArrayList<VariableDeclaration> = ArrayList()
)
