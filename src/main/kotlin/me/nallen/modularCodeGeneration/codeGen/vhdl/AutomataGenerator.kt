package me.nallen.modularCodeGeneration.codeGen.vhdl

import com.hubspot.jinjava.Jinjava
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.hybridAutomata.*
import me.nallen.modularCodeGeneration.hybridAutomata.Locality
import me.nallen.modularCodeGeneration.parseTree.*
import me.nallen.modularCodeGeneration.parseTree.Variable
import me.nallen.modularCodeGeneration.codeGen.vhdl.Utils.VariableObject
import me.nallen.modularCodeGeneration.logging.Logger
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.system.exitProcess

/**
 * The class that contains methods to do with the generation of a single Automaton
 */
object AutomataGenerator {
    /**
     * Generates a string that represents the given Hybrid Automata
     */
    fun generate(item: HybridAutomata, config: Configuration = Configuration()): String {
        val jinjava = Jinjava()

        val template = this::class.java.classLoader.getResource("templates/vhdl/automata.vhdl").readText()

        // Generate data about the root item
        val rootItem = AutomataFileObject(Utils.createTypeName(item.name))

        // We keep a map of signal names so that we can replace
        val signalNameMap = HashMap<String, String>()

        // Now we need to go through each variable of this Network
        for(variable in item.variables.sortedWith(compareBy({ it.locality }, { it.type }))) {
            // Delayed variables are not currently supported in VHDL, so we currently error out
            if(variable.canBeDelayed()) {
                Logger.error("Delayed variables are currently not supported in VHDL Generation")
                exitProcess(1)
            }

            // Depending on the parametrisation method, we'll do things slightly differently
            if(config.compileTimeParametrisation) {
                // Create a variable object that matches the variable
                val variableObject = VariableObject.create(variable)

                // The signal name that we use depends on the type it is
                if(variable.locality == Locality.EXTERNAL_INPUT)
                    // If it's an external input variable then we use that
                    signalNameMap[variable.name] = variableObject.io
                else
                    // Otherwise we use an internal signal
                    signalNameMap[variable.name] = variableObject.signal

                // We need to add either the signal or the parameter to the base component
                if(variable.locality == Locality.PARAMETER)
                    rootItem.parameters.add(variableObject)
                else
                    rootItem.variables.add(variableObject)
            }
            else {
                // Everything needs to become an external I/O signal, so let's do that
                if(variable.locality == Locality.INTERNAL || variable.locality == Locality.EXTERNAL_OUTPUT) {
                    // Then we need to deal with both the input and output since the value could be relative to the
                    // original, and we need to save the output

                    // Let's start with the input signal
                    val variableObjectIn = VariableObject.create(variable.copy(locality = Locality.EXTERNAL_INPUT), runtimeParametrisation = true)

                    // We still want it to be named correctly though
                    variableObjectIn.locality = variable.locality.getTextualName()

                    // Now we keep track of the variable when we want to use it, and add it
                    signalNameMap[variable.name] = variableObjectIn.io
                    rootItem.variables.add(variableObjectIn)

                    // Now let's deal with the output
                    val variableObjectOut = VariableObject.create(variable.copy(locality = Locality.EXTERNAL_OUTPUT), runtimeParametrisation = true)

                    // And name it correctly
                    variableObjectOut.locality = variable.locality.getTextualName()

                    // And add it
                    rootItem.variables.add(variableObjectOut)
                }
                else {
                    // Otherwise it's either an external input or parameter, which we only need to create an input for
                    val variableObject = VariableObject.create(variable, runtimeParametrisation = true)

                    // Then keep track of it, and add it
                    signalNameMap[variable.name] = variableObject.io
                    rootItem.variables.add(variableObject)
                }
            }
        }

        // Now it's time to create all the functions that we need
        val functionParams = HashMap<String, ArrayList<ParseTreeItem>>()

        // Iterate through every function we want to declare
        for(func in item.functions) {
            // Create a function object
            val functionObject = CustomFunctionObject(
                    Utils.createFunctionName(func.name),
                    Utils.generateBasicVHDLType(func.returnType)
            )

            // We keep track of (extra) parameters that are needed for each function call. This will basically just be
            // parameters that are fed into this automaton
            functionParams[func.name] = ArrayList()

            // Iterate over every input to the function
            for(input in func.inputs) {
                // And then we want to add that to the function object
                functionObject.inputs.add(VariableObject.create(me.nallen.modularCodeGeneration.hybridAutomata.Variable(input.name, input.type, Locality.EXTERNAL_INPUT, input.defaultValue)))
            }

            // We also need to find all of the internal variables that we need, this is any internal variable which
            // isn't a parameter
            for(internal in func.logic.variables.filter({it.locality == ParseTreeLocality.INTERNAL})
                    .filterNot({item.variables.any { search -> search.locality == Locality.PARAMETER && search.name == it.name }})) {
                // And then we can add internal variables
                functionObject.variables.add(VariableObject.create(me.nallen.modularCodeGeneration.hybridAutomata.Variable(internal.name, internal.type, Locality.INTERNAL, internal.defaultValue)))
            }

            // If we are doing run-time parametrisation then we also need to deal with parameters
            if(config.runTimeParametrisation) {
                // We want to go through each parameter that is used in this function
                for(internal in func.logic.variables.filter({it.locality == ParseTreeLocality.INTERNAL})
                        .filter({item.variables.any { search -> search.locality == Locality.PARAMETER && search.name == it.name }})) {
                    // Add it as an external input
                    functionObject.inputs.add(VariableObject.create(me.nallen.modularCodeGeneration.hybridAutomata.Variable(internal.name, internal.type, Locality.EXTERNAL_INPUT, internal.defaultValue)))

                    // And keep track of the extra parameter that needs to be added to functions
                    functionParams[func.name]!!.add(Variable(internal.name))
                }
            }

            // Now we want to generate the internal logic for the function
            functionObject.logic.addAll(Utils.generateCodeForProgram(func.logic).split("\n"))

            // And record the function so that we can add it to the end file
            rootItem.customFunctions.add(functionObject)
        }

        // Now let's sort out some state logic
        rootItem.enumName = Utils.createMacroName(item.name, "State")

        // Iterate over every location that we have
        for(location in item.locations) {
            // Now to sort out all the transitions

            // First, we start with the self-transition which uses the invariant for the guard
            val transitionList = arrayListOf(
                    TransitionObject(
                            Utils.generateCodeForParseTreeItem(location.invariant, Utils.PrefixData("", signalNameMap, functionParams)),
                            ArrayList(),
                            ArrayList(),
                            location.name,
                            Utils.createMacroName(item.name, location.name)
                    )
            )

            // We want to keep track of signals that have been updated via flow so that we can use those signals when
            // doing updates later
            val updatedFlowMap = HashMap(signalNameMap)

            // Each flow constraint will also be included in the self-transition, so let's add each of those
            for((variable, ode) in location.flow) {
                // Forward Euler is used to solve each ODE, so create the solution
                val eulerSolution = Plus(Variable(variable), Multiply(ode, Variable("step_size")))
                // And add it to the transition
                transitionList.first().flow.add(UpdateObject(Utils.createVariableName(variable, "update"), Utils.generateCodeForParseTreeItem(eulerSolution, Utils.PrefixData("", signalNameMap, functionParams))))

                // Also keeping track of the updated signal
                updatedFlowMap[variable] = Utils.createVariableName(variable, "update")
            }

            // Now we can add each update of the location to the self transition
            for((variable, equation) in location.update) {
                // Here, we use the updatedFlowMap from before to replace signal names if possible
                transitionList.first().update.add(UpdateObject(Utils.createVariableName(variable, "update"), Utils.generateCodeForParseTreeItem(equation, Utils.PrefixData("", updatedFlowMap, functionParams))))
            }

            //TODO: Saturation

            // Now we can add all other transitions, so iterate over each of them
            for((_, toLocation, guard, update) in item.edges.filter{it.fromLocation == location.name }) {
                // Create the transition
                val transitionObject = TransitionObject(
                        Utils.generateCodeForParseTreeItem(guard, Utils.PrefixData("", signalNameMap, functionParams)),
                        ArrayList(),
                        ArrayList(),
                        toLocation,
                        Utils.createMacroName(item.name, toLocation)
                )

                // And add each update of the transition
                for((variable, equation) in update) {
                    transitionObject.update.add(UpdateObject(Utils.createVariableName(variable, "update"), Utils.generateCodeForParseTreeItem(equation, Utils.PrefixData("", signalNameMap, functionParams))))
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

        // Create the context
        val context = AutomataFileContext(
                config,
                rootItem
        )

        val res = jinjava.renderForResult(template, context.map)

        // And generate!
        return res.output
    }

    /**
     * The class which stores the context for this automaton file
     */
    data class AutomataFileContext(val map: MutableMap<String, Any?>) {
        // Config file for the generation
        var config: Configuration by map

        // Information about the file to be generated
        var item: AutomataFileObject by map

        constructor(config: Configuration, item: AutomataFileObject) : this(
                mutableMapOf(
                        "config" to config,
                        "item" to item
                )
        )
    }

    /**
     * The actual information for the automaton file that we need to generate
     */
    data class AutomataFileObject(
            // The name of the Automaton
            var name: String,

            // A list of parameters which can be declared for this automaton
            var parameters: MutableList<VariableObject> = ArrayList(),

            // A list of variables (including external signals + internal signals) that are used in this automaton
            var variables: MutableList<VariableObject> = ArrayList(),

            // A list of custom functions that need to be declared for the operation
            var customFunctions: MutableList<CustomFunctionObject> = ArrayList(),

            // The name of the enum used for locations
            var enumName: String = "",

            // The list of locations within the automaton (which also contain their transitions)
            var locations: MutableList<LocationObject> = ArrayList(),

            // The initial location for the automaton to start execution in
            var initialLocation: String = ""
    )

    /**
     * A class which stores information about custom functions that need to be declared
     */
    data class CustomFunctionObject(
            // The name of the funtion
            var name: String,

            // The return type of the function
            var returnType: String,

            // A list of any inputs that are required when the function is called
            var inputs: MutableList<VariableObject> = ArrayList(),

            // A list of internal variables of the function
            var variables: MutableList<VariableObject> = ArrayList(),

            // The logic (body) of the function
            var logic: MutableList<String> = ArrayList()
    )

    /**
     * A class which contains all information about a single location of the automaton
     */
    data class LocationObject(
            // The human-readable name of the location
            var name: String,

            // The macro name of the location which is used by the Enum
            var macroName: String,

            // A list of transitions that exit this location
            var transitions: MutableList<TransitionObject>
    )

    /**
     * A class which captures a single transition between two locations (or self-transitions of the same location)
     */
    data class TransitionObject(
            // The guard of the transition which must be satisfied for the transition to occur
            var guard: String,

            // A list of variables that are to be updated due to "Flow" constraints (only used for self-transitions)
            var flow: MutableList<UpdateObject>,

            // A list of variables that are to be updated due to "Update" constraints
            var update: MutableList<UpdateObject>,

            // The human-readable name of the next location that this transition goes to
            var nextStateName: String,

            // The macro name of the next location that this transition goes to
            var nextState: String
    )

    /**
     * A class which captures an assignment to a variable from an equation
     */
    data class UpdateObject(
            // The variable to assign to (left-hand side of the equation)
            var variable: String,

            // The value to assign to the variable (right-hand side of the equation)
            var equation: String
    )
}