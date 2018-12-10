package me.nallen.modularCodeGeneration.codeGen.vhdl

import com.hubspot.jinjava.Jinjava
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.hybridAutomata.*
import me.nallen.modularCodeGeneration.hybridAutomata.Locality
import me.nallen.modularCodeGeneration.parseTree.*
import me.nallen.modularCodeGeneration.parseTree.Variable
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * The class that contains methods to do with the generation of Header Files for the Hybrid Item
 */
object AutomataGenerator {
    /**
     * Generates a string that represents the Header File for the given Hybrid Item
     */
    fun generate(item: HybridAutomata, config: Configuration = Configuration()): String {
        val jinjava = Jinjava()

        val template = this::class.java.classLoader.getResource("templates/vhdl/automata.vhdl").readText()

        // Generate data about the root item
        val rootItem = AutomataFileObject(item.name)

        rootItem.enumName = Utils.createMacroName(item.name, "State")
        for(location in item.locations) {
            // Work out all transitions
            val transitionList = arrayListOf(
                    TransitionObject(
                            Utils.generateCodeForParseTreeItem(location.invariant),
                            ArrayList(),
                            location.name,
                            Utils.createMacroName(item.name, location.name)
                    )
            )

            for((variable, ode) in location.flow) {
                val eulerSolution = Plus(Variable(variable), Multiply(ode, Variable("step_size")))
                transitionList.first().update.add(UpdateObject(Utils.createVariableName(variable), Utils.generateCodeForParseTreeItem(eulerSolution)))
            }

            for((variable, equation) in location.update) {
                transitionList.first().update.add(UpdateObject(Utils.createVariableName(variable), Utils.generateCodeForParseTreeItem(equation)))
            }

            //TODO: Saturation

            for((_, toLocation, guard, update) in item.edges.filter{it.fromLocation == location.name }) {
                val transitionObject = TransitionObject(
                        Utils.generateCodeForParseTreeItem(guard),
                        ArrayList(),
                        toLocation,
                        Utils.createMacroName(item.name, toLocation)
                )

                for((variable, equation) in update) {
                    transitionObject.update.add(UpdateObject(Utils.createVariableName(variable), Utils.generateCodeForParseTreeItem(equation)))
                }

                transitionList.add(transitionObject)
            }

            // Create an entry for it
            val locationObject = LocationObject(
                    location.name,
                    Utils.createMacroName(item.name, location.name),
                    transitionList
            )

            // Add the entry
            rootItem.locations.add(locationObject)
        }
        rootItem.initialLocation = Utils.createMacroName(item.name, item.init.state)

        for(variable in item.variables.sortedWith(compareBy({ it.locality }, { it.type }))) {
            if(variable.canBeDelayed()) {
                throw NotImplementedError("Delayed variables are currently not supported in VHDL Generation")
            }

            val default: ParseTreeItem? = if(variable.locality == Locality.PARAMETER) {
                variable.defaultValue
            } else {
                item.init.valuations[variable.name]
            }

            var defaultValue: Any = Utils.generateDefaultInitForType(variable.type)
            var defaultValueString = "Unassigned default value"
            if(default != null) {
                defaultValue = default.evaluate()
                defaultValueString = default.getString()

                if(defaultValue is Boolean)
                    defaultValue = if(defaultValue) { "'1'" } else { "'0'" }
                else if(defaultValue is Double)
                    defaultValue = "to_signed(${Utils.convertToFixedPoint(defaultValue)}, 32)"
            }

            val variableObject = VariableObject(
                    variable.locality.getTextualName(),
                    Utils.generateVHDLType(variable.type),
                    Utils.createVariableName(variable.name),
                    defaultValue.toString(),
                    defaultValueString
            )

            rootItem.variables.add(variableObject)
        }

        // Create the context
        val context = AutomataFileContext(
                config,
                rootItem
        )

        val res = jinjava.renderForResult(template, context.map)

        // And generate!
        return res.output
    }

    data class AutomataFileContext(val map: MutableMap<String, Any?>) {
        var config: Configuration by map
        var item: AutomataFileObject by map

        constructor(config: Configuration, item: AutomataFileObject) : this(
                mutableMapOf(
                        "config" to config,
                        "item" to item
                )
        )
    }

    data class AutomataFileObject(
            var name: String,
            var variables: MutableList<VariableObject> = ArrayList(),
            var enumName: String = "",
            var locations: MutableList<LocationObject> = ArrayList(),
            var initialLocation: String = ""
    )

    data class VariableObject(
            var locality: String,
            var type: String,
            var variable: String,
            var initialValue: String,
            var initialValueString: String
    )

    data class LocationObject(
            var name: String,
            var macroName: String,
            var transitions: MutableList<TransitionObject>
    )

    data class TransitionObject(
            var guard: String,
            var update: MutableList<UpdateObject>,
            var nextStateName: String,
            var nextState: String
    )

    data class UpdateObject(
            var variable: String,
            var equation: String
    )
}