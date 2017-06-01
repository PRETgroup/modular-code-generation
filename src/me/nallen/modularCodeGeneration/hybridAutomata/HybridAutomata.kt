package me.nallen.modularCodeGeneration.hybridAutomata

import me.nallen.modularCodeGeneration.parseTree.Literal
import me.nallen.modularCodeGeneration.parseTree.ParseTreeItem

/**
 * Created by nall426 on 31/05/2017.
 */

data class HybridAutomata(
        var name: String = "HA",
        var locations: MutableList<Location> = ArrayList<Location>(),
        var edges: MutableList<Edge> = ArrayList<Edge>(),
        var init: Initialisation = Initialisation()
) {
    //private var continuousVariables = ArrayList<Variable>() // TODO: Keep track of continuous variables
    //private var events = ArrayList<Variable>() // TODO: Keep track of events

    fun addLocation(
            name: String,
            invariant: ParseTreeItem = Literal("true"),
            flow: List<ParseTreeItem> = ArrayList<ParseTreeItem>(),
            update: List<ParseTreeItem> = ArrayList<ParseTreeItem>()
    ): HybridAutomata {
        return addLocation(Location(name, invariant, flow, update))
    }

    fun addLocation(location: Location): HybridAutomata {
        if(locations.any({it.name == location.name}))
            throw IllegalArgumentException("Location with name ${location.name} already exists!")

        //TODO: Check if location introduces any new continuousVariables (invariant, flow, update)

        locations.add(location)

        return this
    }

    fun addEdge(
            fromLocation: String,
            toLocation: String,
            guard: ParseTreeItem = Literal("true"),
            update: List<ParseTreeItem> = ArrayList<ParseTreeItem>()
    ): HybridAutomata {
        return addEdge(Edge(fromLocation, toLocation, guard, update))
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

        //TODO: Check if edge introduces any new continuousVariables (guard, update)

        //TODO: Check if edge introudces any new events

        edges.add(edge)

        return this
    }

    //TODO: Adding init should check that all continuousVariables are defined, otherwise default to 0 and WARN
}

data class Location(
        var name: String,
        var invariant: ParseTreeItem = Literal("true"),
        var flow: List<ParseTreeItem> = ArrayList<ParseTreeItem>(),
        var update: List<ParseTreeItem> = ArrayList<ParseTreeItem>()
)

data class Edge(
        var fromLocation: String,
        var toLocation: String,
        var guard: ParseTreeItem = Literal("true"),
        var update: List<ParseTreeItem> = ArrayList<ParseTreeItem>()
)

data class Initialisation(
        var state: Int = -1,
        var valuations: String = ""
)

data class Variable(
        var name: String,
        var locality: Locality
)

enum class Locality {
    INTERNAL, EXTERNAL_INPUT, EXTERNAL_OUTPUT
}
