package me.nallen.modularCodeGeneration.codeGen.vhdl

import com.hubspot.jinjava.Jinjava
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.ParametrisationMethod
import me.nallen.modularCodeGeneration.codeGen.vhdl.Utils.VariableObject
import me.nallen.modularCodeGeneration.hybridAutomata.*
import me.nallen.modularCodeGeneration.hybridAutomata.Locality
import me.nallen.modularCodeGeneration.hybridAutomata.Variable
import me.nallen.modularCodeGeneration.logging.Logger
import me.nallen.modularCodeGeneration.parseTree.And
import me.nallen.modularCodeGeneration.parseTree.ParseTreeItem
import me.nallen.modularCodeGeneration.parseTree.VariableType
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.system.exitProcess

/**
 * The class that contains methods to do with the generation of a Hybrid Network
 */
object NetworkGenerator {
    /**
     * Generates a string that represents the given Hybrid Network
     */
    fun generate(item: HybridNetwork, config: Configuration = Configuration()): String {
        val jinjava = Jinjava()

        val template = this::class.java.classLoader.getResource("templates/vhdl/network.vhdl").readText()

        // Generate data about the root item
        val rootItem = NetworkFileObject(Utils.createTypeName(item.name))

        // We keep a map of signal names so that we can replace
        val signalNameMap = HashMap<String, String>()

        // Now we need to go through each variable of this Network
        for(variable in item.variables.sortedWith(compareBy({ it.locality }, { it.type }))) {
            // Delayed variables are not currently supported in VHDL, so we currently error out
            if(variable.canBeDelayed()) {
                Logger.error("Delayed variables are currently not supported in VHDL Generation")
                exitProcess(1)
            }

            // Now let's create the variable object and add it
            val variableObject = VariableObject.create(variable, runtimeParametrisation = config.runTimeParametrisation)

            // The signal name that we use depends on the type it is
            if(variable.locality == Locality.EXTERNAL_INPUT || variable.locality == Locality.EXTERNAL_OUTPUT || (variable.locality == Locality.PARAMETER && config.runTimeParametrisation))
                // If it's an external variable then we use that
                signalNameMap[variable.name] = variableObject.io
            else
                // Otherwise we use an internal signal
                signalNameMap[variable.name] = variableObject.signal

            // Now we need to either add a variable or a parameter (to be declared as a generic)
            if(variable.locality == Locality.PARAMETER && !config.runTimeParametrisation)
                rootItem.parameters.add(variableObject)
            else
                rootItem.variables.add(variableObject)
        }

        // Depending on the parametrisation method, we'll do things slightly differently
        if(config.compileTimeParametrisation) {
            // We only want to generate each definition once (because generics), so keep track of them
            val generated = ArrayList<UUID>()

            // Let's go through each instance in the network
            for((name, instance) in item.instances) {
                // Get the instantiate of the item we want to generate
                val instantiate = item.getInstantiateForInstantiateId(instance.instantiate)
                if (instantiate != null) {
                    // Get the definition
                    val definition = item.getDefinitionForDefinitionId(instantiate.definition) ?: throw IllegalArgumentException("Unable to find base machine ${instantiate.name} to instantiate!")

                    // Create a component for the thing we need to create
                    val component = ComponentObject(
                            Utils.createTypeName(definition.name)
                    )

                    // Create an instance that will be executed
                    val instanceObject = InstanceObject(
                            Utils.createVariableName(instantiate.name),
                            Utils.createVariableName(instantiate.name, "inst"),
                            Utils.createTypeName(definition.name)
                    )

                    // Now we want to add any parameters to the instance that will be assigned
                    for ((param, value) in instance.parameters) {
                        // Each parameter gets mapped parameter to value
                        instanceObject.parameters.add(MappingObject(
                                Utils.createVariableName(param),
                                Utils.generateCodeForParseTreeItem(value)
                        ))
                    }

                    // Now we want to go through all of the variables of the instance and add them if required
                    for (variable in definition.variables.sortedWith(compareBy({ it.locality }, { it.type }))) {
                        // Delayed variables are not currently supported in VHDL, so we currently error out
                        if (variable.canBeDelayed()) {
                            Logger.error("Delayed variables are currently not supported in VHDL Generation")
                            exitProcess(1)
                        }

                        // Create the inner variable object
                        val variableObject = VariableObject.create(variable)

                        // If we come across a parameter then we add it as a parameter (to be declared as a generic)
                        if (variable.locality == Locality.PARAMETER)
                            component.parameters.add(variableObject)
                        else
                            component.variables.add(variableObject)

                        // If we come across an external I/O variable then we want to add that as a mapping
                        if (variable.locality == Locality.EXTERNAL_OUTPUT || variable.locality == Locality.EXTERNAL_INPUT) {
                            // We need to add a local signal that maps to this I/O
                            val localSignal = VariableObject.create(Variable(Utils.createVariableName(name, variable.name), variable.type, Locality.INTERNAL, variable.defaultValue, variable.delayableBy))
                            rootItem.variables.add(localSignal)

                            // And then add a mapping between the local signal and that I/O
                            instanceObject.mappings.add(MappingObject(
                                    Utils.createVariableName(variable.name, variable.locality.getShortName()),
                                    Utils.createVariableName(instanceObject.name, variable.name)
                            ))

                            // And keep track of the local signal that we just made
                            signalNameMap["${name}.${variable.name}"] = localSignal.signal
                        }
                    }

                    // We only need to add the component once, so make sure we haven't already generated it
                    if (!generated.contains(instantiate.definition)) {
                        generated.add(instantiate.definition)

                        rootItem.components.add(component)
                    }

                    // And we always want to add the instance
                    rootItem.instances.add(instanceObject)
                }
            }
        }
        else {
            // We only want to generate each definition once (because run-time parametrisation), so keep track of them
            val generated = ArrayList<UUID>()

            var processDoneEquation: ParseTreeItem? = null

            for((name, instance) in item.instances) {
                // Get the instantiate of the item we want to generate
                val instantiate = item.getInstantiateForInstantiateId(instance.instantiate)
                if (instantiate != null) {
                    // Get the definition
                    val definition = item.getDefinitionForDefinitionId(instantiate.definition) ?: throw IllegalArgumentException("Unable to find base machine ${instantiate.name} to instantiate!")

                    // Create a component for the thing we need to create
                    val component = ComponentObject(
                            Utils.createTypeName(definition.name)
                    )

                    // Create an instance that will be executed
                    val instanceObject = InstanceObject(
                            Utils.createVariableName(instantiate.name),
                            Utils.createVariableName(instantiate.name, "inst"),
                            Utils.createTypeName(definition.name)
                    )

                    // We want to keep track of renamed variables within this instance for use with initial values
                    val instanceSignalNameMap = HashMap<String, String>()

                    // For run-time parametrisation we need a start and finish signal to be mapped
                    instanceObject.mappings.add(MappingObject(
                            "start",
                            Utils.createVariableName(instanceObject.id, "start")
                    ))

                    instanceObject.mappings.add(MappingObject(
                            "finish",
                            Utils.createVariableName(instanceObject.id, "finish")
                    ))

                    // Now we want to create something which stores all the data that we need for executing this
                    // instance
                    val runtimeMappingObject = RuntimeMappingObject()

                    // The process which deals with executing each of the instances needs to know about a few things
                    val runtimeMappingProcess = RuntimeMappingProcessObject(
                            Utils.createVariableName(instanceObject.name, "proc"),
                            Utils.createVariableName(instanceObject.id, "start"),
                            Utils.createVariableName(instanceObject.id, "finish"),
                            Utils.createVariableName(instanceObject.name, "proc", "done"),
                            Utils.createVariableName(instanceObject.name, "proc", "start")
                    )

                    // And then we want to create the variables needed for starting and stopping the execution
                    runtimeMappingProcess.variables.add(VariableObject.create(Variable(Utils.createVariableName(instanceObject.id, "start"), VariableType.BOOLEAN, Locality.INTERNAL)))
                    runtimeMappingProcess.variables.add(VariableObject.create(Variable(Utils.createVariableName(instanceObject.id, "finish"), VariableType.BOOLEAN, Locality.INTERNAL)))

                    // Now, if the instance is an automata then we also need to extract the state
                    if(definition is HybridAutomata) {
                        // We create the variable for storing the state, which is stored as an integer with an
                        // appropriate size
                        val variableObjectIn = VariableObject.create(Variable("state", VariableType.INTEGER, Locality.EXTERNAL_INPUT), runtimeParametrisation = true)
                        variableObjectIn.type = "integer range 0 to ${definition.locations.size-1}"
                        component.variables.add(variableObjectIn)

                        // And then map the state to the instance
                        instanceObject.mappings.add(MappingObject(
                                Utils.createVariableName(variableObjectIn.io),
                                Utils.createVariableName(instanceObject.id, "state", "in")
                        ))

                        // Now we create the local signal that maps to the above
                        runtimeMappingProcess.variables.add(VariableObject.create(Variable(Utils.createVariableName(instanceObject.id, "state", "in"), VariableType.INTEGER, Locality.INTERNAL)))
                        runtimeMappingProcess.variables.last().type = variableObjectIn.type

                        // Now, similarly, we create the variable for storing the state output (i.e. the new value)
                        val variableObjectOut = VariableObject.create(Variable("state", VariableType.INTEGER, Locality.EXTERNAL_OUTPUT), runtimeParametrisation = true)
                        variableObjectOut.type = variableObjectIn.type
                        component.variables.add(variableObjectOut)

                        // Map the state output to the instance
                        instanceObject.mappings.add(MappingObject(
                                Utils.createVariableName(variableObjectOut.io),
                                Utils.createVariableName(instanceObject.id, "state", "out")
                        ))

                        // And create the local signal for the mapping
                        runtimeMappingProcess.variables.add(VariableObject.create(Variable(Utils.createVariableName(instanceObject.id, "state", "out"), VariableType.INTEGER, Locality.INTERNAL)))
                        runtimeMappingProcess.variables.last().type = variableObjectIn.type

                        // Now we create a local signal that actually stores the value for each instance, this will also
                        // be initialised correctly with the initial state
                        val localSignal = VariableObject.create(Variable(Utils.createVariableName(name, "state"), VariableType.INTEGER, Locality.INTERNAL))
                        localSignal.initialValue = definition.locations.indexOfFirst { it.name == definition.init.state }.toString()
                        localSignal.initialValueString = definition.init.state
                        localSignal.type = variableObjectIn.type

                        rootItem.variables.add(localSignal)

                        // And now let's create the mapping from that storage to the instance in signal
                        runtimeMappingObject.mappingsIn.add(MappingObject(
                                Utils.createVariableName(instanceObject.id, "state", "in"),
                                localSignal.signal
                        ))

                        // And the same for the instance output
                        runtimeMappingObject.mappingsOut.add(MappingObject(
                                localSignal.signal,
                                Utils.createVariableName(instanceObject.id, "state", "out")
                        ))

                        // And keep track of the signal name as always
                        signalNameMap["${name}.state"] = localSignal.signal
                        instanceSignalNameMap["state"] = localSignal.signal
                    }

                    // Now we need to just go through every variable and deal with those
                    for (variable in definition.variables.sortedWith(compareBy({ it.locality }, { it.type }))) {
                        // Delayed variables are not currently supported in VHDL, so we currently error out
                        if (variable.canBeDelayed()) {
                            Logger.error("Delayed variables are currently not supported in VHDL Generation")
                            exitProcess(1)
                        }

                        // Let's try and find a default value for this variable
                        val defaultValue = if (instance.parameters.containsKey(variable.name)) {
                            // Start with seeing if it's a parameter and has a value
                            instance.parameters[variable.name]
                        } else if (definition is HybridAutomata && definition.init.valuations.containsKey(variable.name)) {
                            // Or if there's an initialisation for the Automata
                            definition.init.valuations[variable.name]
                        } else {
                            // Otherwise just try use the default value associated on the variable
                            variable.defaultValue
                        }

                        // If the variable is an external output or an internal variable
                        if(variable.locality == Locality.EXTERNAL_OUTPUT || variable.locality == Locality.INTERNAL) {
                            // Then we need to deal with both the input and output to/from the instance since the value
                            // could be relative to the original, and we need to save the output

                            // So let's create an input signal
                            val variableObjectIn = VariableObject.create(variable.copy(defaultValue = defaultValue, locality = Locality.EXTERNAL_INPUT), runtimeParametrisation = true)
                            component.variables.add(variableObjectIn)

                            // And map the input signal to the instance
                            instanceObject.mappings.add(MappingObject(
                                    Utils.createVariableName(variableObjectIn.io),
                                    Utils.createVariableName(instanceObject.id, variable.name, "in")
                            ))

                            // And add an input signal
                            runtimeMappingProcess.variables.add(VariableObject.create(Variable(Utils.createVariableName(instanceObject.id, variable.name, "in"), variable.type, Locality.INTERNAL)))

                            // Similarly, create an output signal
                            val variableObjectOut = VariableObject.create(variable.copy(defaultValue = defaultValue, locality = Locality.EXTERNAL_OUTPUT), runtimeParametrisation = true)
                            component.variables.add(variableObjectOut)

                            // Map it  to the instance
                            instanceObject.mappings.add(MappingObject(
                                    Utils.createVariableName(variableObjectOut.io),
                                    Utils.createVariableName(instanceObject.id, variable.name, "out")
                            ))

                            // And add an output signal
                            runtimeMappingProcess.variables.add(VariableObject.create(Variable(Utils.createVariableName(instanceObject.id, variable.name, "out"), variable.type, Locality.INTERNAL)))
                        }
                        else {
                            // Otherwise it's either an external input or parameter, which we just need to worry about
                            // inputting to the instance but nothing about output
                            val variableObject = VariableObject.create(variable.copy(defaultValue = defaultValue), runtimeParametrisation = true)
                            component.variables.add(variableObject)

                            // Map the input signal to the instance
                            instanceObject.mappings.add(MappingObject(
                                    Utils.createVariableName(variableObject.io),
                                    Utils.createVariableName(instanceObject.id, variable.name)
                            ))

                            // Add an input signal
                            runtimeMappingProcess.variables.add(VariableObject.create(Variable(Utils.createVariableName(instanceObject.id, variable.name), variable.type, Locality.INTERNAL)))
                        }

                        // Now we create the local signal for storing the value of the variable for this instance
                        val localSignal = VariableObject.create(Variable(Utils.createVariableName(name, variable.name), variable.type, Locality.INTERNAL, defaultValue, variable.delayableBy), prefixData=Utils.PrefixData("", instanceSignalNameMap))
                        // We still want labels for parameters to be correct because we'll make them constants
                        if(variable.locality == Locality.PARAMETER)
                            localSignal.locality = Locality.PARAMETER.getTextualName()

                        rootItem.variables.add(localSignal)

                        // Now we will map that signal
                        if(variable.locality == Locality.EXTERNAL_INPUT || variable.locality == Locality.PARAMETER) {
                            // For values that we only need to assign (not read) we can map that
                            runtimeMappingObject.mappingsIn.add(MappingObject(
                                    Utils.createVariableName(instanceObject.id, variable.name),
                                    localSignal.signal
                            ))
                        }
                        else if(variable.locality == Locality.EXTERNAL_OUTPUT || variable.locality == Locality.INTERNAL) {
                            // Now for outputs or internal variables we need to both assign and read them
                            runtimeMappingObject.mappingsIn.add(MappingObject(
                                    Utils.createVariableName(instanceObject.id, variable.name, "in"),
                                    localSignal.signal
                            ))

                            runtimeMappingObject.mappingsOut.add(MappingObject(
                                    localSignal.signal,
                                    Utils.createVariableName(instanceObject.id, variable.name, "out")
                            ))
                        }

                        // And keep track of the local signal that we just made
                        signalNameMap["${name}.${variable.name}"] = localSignal.signal
                        instanceSignalNameMap[variable.name] = localSignal.signal
                    }

                    // We only need to add the component and instance once, so make sure we haven't already generated it
                    if (!generated.contains(instantiate.definition)) {
                        generated.add(instantiate.definition)

                        rootItem.components.add(component)

                        rootItem.instances.add(instanceObject)

                        // We only need a single process for each instance too
                        rootItem.runtimeMappingProcesses.add(runtimeMappingProcess)

                        // And we make an equation which tells the master thread when everything is all done, it will
                        // just be each processes done signals ANDed together
                        processDoneEquation = if(processDoneEquation == null)
                            ParseTreeItem.generate(Utils.createVariableName(instanceObject.name, "proc", "done"))
                        else
                            And(processDoneEquation, ParseTreeItem.generate(Utils.createVariableName(instanceObject.name, "proc", "done")))
                    }

                    // And then we want to run this instantiate
                    rootItem.runtimeMappingProcesses.first { it.name.equals(Utils.createVariableName(instanceObject.name, "proc")) }.runtimeMappings.add(runtimeMappingObject)
                }
            }

            // And now we record the master thread done signal
            if(processDoneEquation != null)
                rootItem.runtimeProcessDoneSignal = Utils.generateCodeForParseTreeItem(processDoneEquation)
        }

        // Now we need to do all of the I/O Mapping, so iterate over each of them
        for((destination, value) in item.ioMapping) {
            // There's two types of mappings we could have, where we assign to an automata variable, or a local signal
            if(destination.automata.isEmpty()) {
                // If it's a local signal, then we want to check signalNameMap
                rootItem.mappings.add(MappingObject(
                        Utils.createVariableName(signalNameMap[destination.variable] ?: destination.variable),
                        Utils.generateCodeForParseTreeItem(value, Utils.PrefixData("", signalNameMap))
                ))
            }
            else {
                // Otherwise we use the automata name
                rootItem.mappings.add(MappingObject(
                        Utils.createVariableName(destination.automata, destination.variable),
                        Utils.generateCodeForParseTreeItem(value, Utils.PrefixData("", signalNameMap))
                ))
            }
        }

        // Create the context
        val context = NetworkFileContext(
                config,
                rootItem
        )

        val res = jinjava.renderForResult(template, context.map)

        // And generate!
        return res.output
    }

    /**
     * The class which stores the context for this network file
     */
    data class NetworkFileContext(val map: MutableMap<String, Any?>) {
        // Config file for the generation
        var config: Configuration by map

        // Information about the file to be generated
        var item: NetworkFileObject by map

        constructor(config: Configuration, item: NetworkFileObject) : this(
                mutableMapOf(
                        "config" to config,
                        "item" to item
                )
        )
    }

    /**
     * The actual information for the network file that we need to generate
     */
    data class NetworkFileObject(
            // The name of the network
            var name: String,

            // A list of parameters that are used by this network
            var parameters: MutableList<VariableObject> = ArrayList(),

            // A list of variables to be declared in the network
            var variables: MutableList<VariableObject> = ArrayList(),

            // A list of components that are to be declared in this network
            var components: MutableList<ComponentObject> = ArrayList(),

            // A list of instances that are to be instantiated in this network
            var instances: MutableList<InstanceObject> = ArrayList(),

            // The Mappings between variables that need to be done once per tick
            var mappings: MutableList<MappingObject> = ArrayList(),

            // If run-time parametrisation is being done then this will be a list of run-time processes to be
            // declared which will do all of the execution for each instantiate of each type
            var runtimeMappingProcesses: MutableList<RuntimeMappingProcessObject> = ArrayList(),

            // If run-time parametrisation is being done then this will be the equation which says when each process
            // has finished and execution can continue
            var runtimeProcessDoneSignal: String = "true"
    )

    /**
     * Information about a component that needs to be declared
     */
    data class ComponentObject(
            // The name of the component
            var name: String,

            // The list of parameters that are used by the component
            var parameters: MutableList<VariableObject> = ArrayList(),

            // The list of variables (including External I/O) that are part of the component
            var variables: MutableList<VariableObject> = ArrayList()
    )

    /**
     * Information about a single instance that needs to be instantiated
     */
    data class InstanceObject(
            // The name of the instance
            var name: String,

            // The ID to be used to identify the instance
            var id: String,

            // The type to instantiate, this will be the name of one of the components listed in the network
            var type: String,

            // A list of parameter values to use to configure the component
            var parameters: MutableList<MappingObject> = ArrayList(),

            // A list of mappings of signals that are to be fed into the component
            var mappings: MutableList<MappingObject> = ArrayList()
    )

    /**
     * A simple object which details a mapping with a left-hand side and a right-hand side
     */
    data class MappingObject(
            // The left part of the mapping (i.e. what gets assigned to)
            var left: String,

            // the right part of the mapping (i.e. the value that gets assigned)
            var right: String
    )

    /**
     * An class which details a process which is used to sequentialise the execution of a single instance in run-time
     * parametrisation
     */
    data class RuntimeMappingProcessObject(
            // The name of the process
            var name: String,

            // The signal that's used to tell the relevant instance to start execution
            var startSignal: String,

            // The signal that's used to signify when the relevant instance has finished its current execution
            var finishSignal: String,

            // The signal that's used to signify that this process has completed execution of all instances it needs to
            var processDoneSignal: String,

            // The signal that's used to tell this process to start its sequence of execution
            var processStartSignal: String,

            // A list of variables that are used within this process
            var variables: MutableList<VariableObject> = ArrayList(),

            // A list of mappings of signals that need to be done on each step of the process
            var runtimeMappings: MutableList<RuntimeMappingObject> = ArrayList()
    )

    /**
     * A class that contains the mappings that are to be done on each step of the execution
     */
    data class RuntimeMappingObject(
            // The list of inputs to be set for the instance
            var mappingsIn: MutableList<MappingObject> = ArrayList(),

            // The list of outputs to be read from the instance
            var mappingsOut: MutableList<MappingObject> = ArrayList()
    )
}