package me.nallen.modularCodeGeneration.hybridAutomata

import me.nallen.modularCodeGeneration.parseTree.Literal
import me.nallen.modularCodeGeneration.parseTree.Variable as ParseTreeVariable
import me.nallen.modularCodeGeneration.parseTree.ParseTreeItem
import me.nallen.modularCodeGeneration.parseTree.getChildren

/**
 * Created by nall426 on 31/05/2017.
 */

data class HybridAutomata(
        var name: String = "HA"
) {
    val locations = ArrayList<Location>()
    val edges = ArrayList<Edge>()
    var init = Initialisation("")

    val continuousVariables = ArrayList<Variable>()
    val events = ArrayList<Variable>()

    fun addLocation(
            name: String,
            invariant: ParseTreeItem = Literal("true"),
            flow: Map<String, ParseTreeItem> = HashMap<String, ParseTreeItem>(),
            update: Map<String, ParseTreeItem> = HashMap<String, ParseTreeItem>()
    ): HybridAutomata {
        return addLocation(Location(name, invariant, flow, update))
    }

    fun addLocation(location: Location): HybridAutomata {
        if(locations.any({it.name == location.name}))
            throw IllegalArgumentException("Location with name ${location.name} already exists!")

        // Check for any continuous variables
        checkParseTreeForNewContinuousVariable(location.invariant)

        for((key, value) in location.flow) {
            addContinuousVariableIfNotExist(key)
            checkParseTreeForNewContinuousVariable(value)
        }

        for((key, value) in location.update) {
            addContinuousVariableIfNotExist(key)
            checkParseTreeForNewContinuousVariable(value)
        }

        locations.add(location)

        return this
    }

    fun addEdge(
            fromLocation: String,
            toLocation: String,
            guard: ParseTreeItem = Literal("true"),
            update: Map<String, ParseTreeItem> = HashMap<String, ParseTreeItem>(),
            inEvents: ParseTreeItem = Literal("true"),
            outEvents: List<String> = ArrayList<String>()
    ): HybridAutomata {
        return addEdge(Edge(fromLocation, toLocation, guard, update, inEvents, outEvents))
    }

    fun addEdge(edge: Edge): HybridAutomata {
        if(!locations.any({ it.name == edge.fromLocation }))
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
        }

        // Check for any continuous variables
        checkParseTreeForNewContinuousVariable(edge.guard)

        for((key, value) in edge.update) {
            addContinuousVariableIfNotExist(key)
            checkParseTreeForNewContinuousVariable(value)
        }

        // Check for any events
        checkParseTreeForNewEvent(edge.inEvents, Locality.EXTERNAL_INPUT)

        for(event in edge.outEvents) {
            addEventIfNotExist(event, Locality.EXTERNAL_OUTPUT)
        }

        edges.add(edge)

        return this
    }

    fun setInit(init: Initialisation): HybridAutomata {
        if(!locations.any({ it.name == init.state }))
            throw IllegalArgumentException("Location with name ${init.state} does not exist!")

        this.init = init

        return this
    }

    /* Private Methods */

    private fun checkParseTreeForNewContinuousVariable(item: ParseTreeItem, locality: Locality = Locality.INTERNAL) {
        if(item is ParseTreeVariable) {
            addContinuousVariableIfNotExist(item.name, locality)
        }

        for(child in item.getChildren()) {
            checkParseTreeForNewContinuousVariable(child)
        }
    }

    private fun addContinuousVariableIfNotExist(item: String, locality: Locality = Locality.INTERNAL) {
        if(!continuousVariables.any({it.name == item})) {
            continuousVariables.add(Variable(item, locality))
        }
    }

    private fun checkParseTreeForNewEvent(item: ParseTreeItem, locality: Locality = Locality.INTERNAL) {
        if(item is ParseTreeVariable) {
            addEventIfNotExist(item.name, locality)
        }

        for(child in item.getChildren()) {
            checkParseTreeForNewEvent(child, locality)
        }
    }

    private fun addEventIfNotExist(item: String, locality: Locality = Locality.INTERNAL) {
        if(!events.any({it.name == item})) {
            events.add(Variable(item, locality))
        }
    }
}

data class Location(
        var name: String,
        var invariant: ParseTreeItem = Literal("true"),
        var flow: Map<String, ParseTreeItem> = HashMap<String, ParseTreeItem>(),
        var update: Map<String, ParseTreeItem> = HashMap<String, ParseTreeItem>()
)

data class Edge(
        var fromLocation: String,
        var toLocation: String,
        var guard: ParseTreeItem = Literal("true"),
        var update: Map<String, ParseTreeItem> = HashMap<String, ParseTreeItem>(),
        var inEvents: ParseTreeItem = Literal("true"),
        var outEvents: List<String> = ArrayList<String>()
)

data class Initialisation(
        var state: String,
        var valuations: Map<String, ParseTreeItem> = HashMap<String, ParseTreeItem>()
)

data class Variable(
        var name: String,
        var locality: Locality
)

enum class Locality {
    INTERNAL, EXTERNAL_INPUT, EXTERNAL_OUTPUT
}
