package me.nallen.modularCodeGeneration.hybridAutomata

import me.nallen.modularCodeGeneration.parseTree.Literal
import me.nallen.modularCodeGeneration.parseTree.Variable as ParseTreeVariable
import me.nallen.modularCodeGeneration.parseTree.ParseTreeItem
import me.nallen.modularCodeGeneration.parseTree.getChildren

/**
 * Created by nall426 on 31/05/2017.
 */

data class HybridAutomata(
        var name: String = "HA",

        val locations: ArrayList<Location> = ArrayList<Location>(),
        val edges: ArrayList<Edge> = ArrayList<Edge>(),
        var init: Initialisation = Initialisation(""),

        val continuousVariables: ArrayList<Variable> = ArrayList<Variable>(),
        val events: ArrayList<Variable> = ArrayList<Variable>()
) {
    fun addLocation(
            name: String,
            invariant: ParseTreeItem = Literal("true"),
            flow: HashMap<String, ParseTreeItem> = HashMap<String, ParseTreeItem>(),
            update: HashMap<String, ParseTreeItem> = HashMap<String, ParseTreeItem>()
    ): HybridAutomata {
        return addLocation(Location(name, invariant, flow, update))
    }

    fun addLocation(location: Location): HybridAutomata {
        if(locations.any({it.name == location.name}))
            throw IllegalArgumentException("Location with name ${location.name} already exists!")

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

    fun addEdge(
            fromLocation: String,
            toLocation: String,
            guard: ParseTreeItem = Literal("true"),
            update: HashMap<String, ParseTreeItem> = HashMap<String, ParseTreeItem>(),
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

    fun setInit(init: Initialisation): HybridAutomata {
        if(!locations.any({ it.name == init.state }))
            throw IllegalArgumentException("Location with name ${init.state} does not exist!")

        this.init = init

        return this
    }

    fun addContinuousVariable(item: String, locality: Locality = Locality.INTERNAL): HybridAutomata {
        if(!continuousVariables.any({it.name == item})) {
            continuousVariables.add(Variable(item, locality))
        }

        return this
    }

    fun addParameter(item: String): HybridAutomata {
        return addContinuousVariable(item, Locality.PARAMETER)
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
        var flow: HashMap<String, ParseTreeItem> = HashMap<String, ParseTreeItem>(),
        var update: HashMap<String, ParseTreeItem> = HashMap<String, ParseTreeItem>()
)

data class Edge(
        var fromLocation: String,
        var toLocation: String,
        var guard: ParseTreeItem = Literal("true"),
        var update: HashMap<String, ParseTreeItem> = HashMap<String, ParseTreeItem>(),
        var inEvents: ParseTreeItem = Literal("true"),
        var outEvents: List<String> = ArrayList<String>()
)

data class Initialisation(
        var state: String,
        var valuations: HashMap<String, ParseTreeItem> = HashMap<String, ParseTreeItem>()
)

data class Variable(
        var name: String,
        var locality: Locality
)

enum class Locality {
    INTERNAL, EXTERNAL_INPUT, EXTERNAL_OUTPUT, PARAMETER
}
