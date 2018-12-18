package me.nallen.modularCodeGeneration.codeGen.vhdl

import com.hubspot.jinjava.Jinjava
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.hybridAutomata.*
import me.nallen.modularCodeGeneration.hybridAutomata.Locality
import me.nallen.modularCodeGeneration.parseTree.*
import me.nallen.modularCodeGeneration.parseTree.Variable
import me.nallen.modularCodeGeneration.codeGen.vhdl.Utils.VariableObject
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
        val rootItem = AutomataFileObject(Utils.createTypeName(item.name))

        val signalNameMap = HashMap<String, String>()

        for(variable in item.variables.sortedWith(compareBy({ it.locality }, { it.type }))) {
            if(variable.canBeDelayed()) {
                throw NotImplementedError("Delayed variables are currently not supported in VHDL Generation")
            }

            val variableObject = VariableObject.create(variable, item.init.valuations)

            if(variable.locality == Locality.EXTERNAL_INPUT)
                signalNameMap[variable.name] = variableObject.io
            else
                signalNameMap[variable.name] = variableObject.signal

            if(variable.locality == Locality.PARAMETER)
                rootItem.parameters.add(variableObject)
            else
                rootItem.variables.add(variableObject)
        }

        rootItem.enumName = Utils.createMacroName(item.name, "State")
        for(location in item.locations) {
            // Work out all transitions
            val transitionList = arrayListOf(
                    TransitionObject(
                            Utils.generateCodeForParseTreeItem(location.invariant, Utils.PrefixData("", signalNameMap)),
                            ArrayList(),
                            ArrayList(),
                            location.name,
                            Utils.createMacroName(item.name, location.name)
                    )
            )

            val updatedFlowMap = HashMap(signalNameMap)

            for((variable, ode) in location.flow) {
                val eulerSolution = Plus(Variable(variable), Multiply(ode, Variable("step_size")))
                transitionList.first().flow.add(UpdateObject(Utils.createVariableName(variable, "update"), Utils.generateCodeForParseTreeItem(eulerSolution, Utils.PrefixData("", signalNameMap))))

                updatedFlowMap[variable] = Utils.createVariableName(variable, "update")
            }

            for((variable, equation) in location.update) {
                transitionList.first().update.add(UpdateObject(Utils.createVariableName(variable, "update"), Utils.generateCodeForParseTreeItem(equation, Utils.PrefixData("", updatedFlowMap))))
            }

            //TODO: Saturation

            for((_, toLocation, guard, update) in item.edges.filter{it.fromLocation == location.name }) {
                val transitionObject = TransitionObject(
                        Utils.generateCodeForParseTreeItem(guard, Utils.PrefixData("", signalNameMap)),
                        ArrayList(),
                        ArrayList(),
                        toLocation,
                        Utils.createMacroName(item.name, toLocation)
                )

                for((variable, equation) in update) {
                    transitionObject.update.add(UpdateObject(Utils.createVariableName(variable, "update"), Utils.generateCodeForParseTreeItem(equation, Utils.PrefixData("", signalNameMap))))
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

        for(func in item.functions) {
            val functionObject = CustomFunctionObject(
                    Utils.createFunctionName(func.name),
                    Utils.generateBasicVHDLType(func.returnType),
                    ArrayList(),
                    ArrayList(),
                    ArrayList()
            )

            for(input in func.inputs) {
                functionObject.inputs.add(VariableObject.create(me.nallen.modularCodeGeneration.hybridAutomata.Variable(input.name, input.type, Locality.EXTERNAL_INPUT, input.defaultValue)))
            }

            for(internal in func.logic.variables.filter({it.locality == ParseTreeLocality.INTERNAL})
                    .filterNot({item.variables.any { search -> search.locality == Locality.PARAMETER && search.name == it.name }})) {
                functionObject.variables.add(VariableObject.create(me.nallen.modularCodeGeneration.hybridAutomata.Variable(internal.name, internal.type, Locality.INTERNAL, internal.defaultValue)))
            }

            functionObject.logic.addAll(Utils.generateCodeForProgram(func.logic).split("\n"))

            rootItem.customFunctions.add(functionObject)
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
            var parameters: MutableList<VariableObject> = ArrayList(),
            var variables: MutableList<VariableObject> = ArrayList(),
            var customFunctions: MutableList<CustomFunctionObject> = ArrayList(),
            var enumName: String = "",
            var locations: MutableList<LocationObject> = ArrayList(),
            var initialLocation: String = ""
    )

    data class CustomFunctionObject(
            var name: String,
            var returnType: String,
            var inputs: MutableList<VariableObject>,
            var variables: MutableList<VariableObject>,
            var logic: MutableList<String>
    )

    data class LocationObject(
            var name: String,
            var macroName: String,
            var transitions: MutableList<TransitionObject>
    )

    data class TransitionObject(
            var guard: String,
            var flow: MutableList<UpdateObject>,
            var update: MutableList<UpdateObject>,
            var nextStateName: String,
            var nextState: String
    )

    data class UpdateObject(
            var variable: String,
            var equation: String
    )
}