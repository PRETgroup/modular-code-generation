package me.nallen.modularcodegeneration.hybridautomata

import me.nallen.modularcodegeneration.parsetree.*
import me.nallen.modularcodegeneration.parsetree.Variable as ParseTreeVariable

/**
 * Created by nall426 on 31/05/2017.
 */

data class HybridAutomata(
        override var name: String = "HA",

        val locations: ArrayList<Location> = ArrayList(),
        val edges: ArrayList<Edge> = ArrayList(),
        var init: Initialisation = Initialisation(""),

        val functions: ArrayList<FunctionDefinition> = ArrayList()
) : HybridItem() {
    fun addLocation(location: Location): HybridAutomata {
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

    override fun setParameterValue(key: String, value: ParseTreeItem) {
        super.setParameterValue(key, value)

        // Parametrise all transitions
        for(edge in edges) {
            // Guards
            edge.guard.setParameterValue(key, value)

            // Updates
            for((_, update) in edge.update) {
                update.setParameterValue(key, value)
            }
        }

        // Parametrise all locations
        for(location in locations) {
            // Invariant
            location.invariant.setParameterValue(key, value)

            // Flows
            for((_, flow) in location.flow) {
                flow.setParameterValue(key, value)
            }

            // Updates
            for((_, update) in location.update) {
                update.setParameterValue(key, value)
            }
        }

        // Parametrise initialisation
        for((_, valuation) in init.valuations) {
            valuation.setParameterValue(key, value)
        }

        // Parametrise functions
        for(function in functions) {
            for(input in function.inputs) {
                input.defaultValue?.setParameterValue(key, value)
            }

            function.logic.setParameterValue(key, value)
        }
    }

    /**
     * Check if this Hybrid Automata is valid, this includes things like variable names being correct, transitions being
     * between valid locations, etc. This can help the user detect errors during the compile stage rather than by
     * analysing the generated code.
     */
    override fun validate(): Boolean {
        // Let's try see if anything isn't valid
        var valid = super.validate()

        return valid
    }
}

data class Location(
        var name: String,
        var invariant: ParseTreeItem = Literal("true"),
        var flow: MutableMap<String, ParseTreeItem> = LinkedHashMap(),
        var update: MutableMap<String, ParseTreeItem> = LinkedHashMap()
)

data class Edge(
        var fromLocation: String,
        var toLocation: String,
        var guard: ParseTreeItem = Literal("true"),
        var update: MutableMap<String, ParseTreeItem> = LinkedHashMap(),
        var inEvents: ParseTreeItem = Literal("true"),
        var outEvents: List<String> = ArrayList()
)

data class Initialisation(
        var state: String,
        var valuations: MutableMap<String, ParseTreeItem> = LinkedHashMap()
)

data class Variable(
        var name: String,
        var type: VariableType,
        var locality: Locality,
        var defaultValue: ParseTreeItem? = null,
        var delayableBy: ParseTreeItem? = null
) {
    fun canBeDelayed(): Boolean = delayableBy != null
}

enum class Locality {
    PARAMETER, EXTERNAL_INPUT, EXTERNAL_OUTPUT, INTERNAL;

    fun getTextualName(): String {
        return when(this) {
            Locality.INTERNAL -> "Internal Variables"
            Locality.EXTERNAL_INPUT -> "Inputs"
            Locality.EXTERNAL_OUTPUT -> "Outputs"
            Locality.PARAMETER -> "Parameters"
        }
    }

    fun getShortName(): String {
        return when(this) {
            Locality.INTERNAL -> "Int"
            Locality.EXTERNAL_INPUT -> "In"
            Locality.EXTERNAL_OUTPUT -> "Out"
            Locality.PARAMETER -> "Param"
        }
    }
}

data class FunctionDefinition(
        var name: String,
        var logic: Program,
        var inputs: ArrayList<VariableDeclaration> = ArrayList(),
        var returnType: VariableType? = null
)
