package me.nallen.modularCodeGeneration.codeGen.vhdl

import com.hubspot.jinjava.Jinjava
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.ParametrisationMethod
import me.nallen.modularCodeGeneration.codeGen.vhdl.Utils.VariableObject
import me.nallen.modularCodeGeneration.hybridAutomata.*
import me.nallen.modularCodeGeneration.hybridAutomata.Locality
import me.nallen.modularCodeGeneration.hybridAutomata.Variable
import me.nallen.modularCodeGeneration.parseTree.And
import me.nallen.modularCodeGeneration.parseTree.ParseTreeItem
import me.nallen.modularCodeGeneration.parseTree.VariableType
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

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

        val signalNameMap = HashMap<String, String>()

        for(variable in item.variables.sortedWith(compareBy({ it.locality }, { it.type }))) {
            if(variable.canBeDelayed()) {
                throw NotImplementedError("Delayed variables are currently not supported in VHDL Generation")
            }

            val variableObject = VariableObject.create(variable, runtimeParametrisation = config.runTimeParametrisation)

            if(variable.locality == Locality.EXTERNAL_INPUT || variable.locality == Locality.EXTERNAL_OUTPUT || (variable.locality == Locality.PARAMETER && config.runTimeParametrisation))
                signalNameMap[variable.name] = variableObject.io
            else
                signalNameMap[variable.name] = variableObject.signal

            if(variable.locality == Locality.PARAMETER && !config.runTimeParametrisation)
                rootItem.parameters.add(variableObject)
            else
                rootItem.variables.add(variableObject)
        }

        // Depending on the parametrisation method, we'll do things slightly differently
        if(config.compileTimeParametrisation) {
            // We only want to generate each definition once (because generics), so keep track of them
            val generated = ArrayList<UUID>()

            for((name, instance) in item.instances) {
                // Get the instance of the item we want to generate
                val instantiate = item.getInstantiateForInstantiateId(instance.instantiate)
                if (instantiate != null) {
                    val definition = item.getDefinitionForDefinitionId(instantiate.definition) ?: throw IllegalArgumentException("Unable to find base machine ${instantiate.name} to instantiate!")

                    val component = ComponentObject(
                            Utils.createTypeName(definition.name)
                    )

                    val instanceObject = InstanceObject(
                            Utils.createVariableName(instantiate.name),
                            Utils.createVariableName(instantiate.name, "inst"),
                            Utils.createTypeName(definition.name)
                    )

                    for ((param, value) in instance.parameters) {
                        instanceObject.parameters.add(MappingObject(
                                Utils.createVariableName(param),
                                Utils.generateCodeForParseTreeItem(value)
                        ))
                    }

                    for (variable in definition.variables.sortedWith(compareBy({ it.locality }, { it.type }))) {
                        if (variable.canBeDelayed()) {
                            throw NotImplementedError("Delayed variables are currently not supported in VHDL Generation")
                        }

                        val variableObject = VariableObject.create(variable)

                        if (variable.locality == Locality.PARAMETER)
                            component.parameters.add(variableObject)
                        else
                            component.variables.add(variableObject)

                        if (variable.locality == Locality.EXTERNAL_OUTPUT || variable.locality == Locality.EXTERNAL_INPUT) {
                            val localSignal = VariableObject.create(Variable(Utils.createVariableName(name, variable.name), variable.type, Locality.INTERNAL, variable.defaultValue, variable.delayableBy))

                            rootItem.variables.add(localSignal)
                            instanceObject.mappings.add(MappingObject(
                                    Utils.createVariableName(variable.name, variable.locality.getShortName()),
                                    Utils.createVariableName(instanceObject.name, variable.name)
                            ))

                            signalNameMap["${name}.${variable.name}"] = localSignal.signal
                        }
                    }

                    if (!generated.contains(instantiate.definition)) {
                        generated.add(instantiate.definition)

                        rootItem.components.add(component)
                    }

                    rootItem.instances.add(instanceObject)
                }
            }
        }
        else {
            // We only want to generate each definition once (because run-time parametrisation), so keep track of them
            val generated = ArrayList<UUID>()

            var processDoneEquation: ParseTreeItem? = null

            for((name, instance) in item.instances) {
                // Get the instance of the item we want to generate
                val instantiate = item.getInstantiateForInstantiateId(instance.instantiate)
                if (instantiate != null) {
                    val definition = item.getDefinitionForDefinitionId(instantiate.definition) ?: throw IllegalArgumentException("Unable to find base machine ${instantiate.name} to instantiate!")

                    val component = ComponentObject(
                            Utils.createTypeName(definition.name)
                    )

                    val instanceObject = InstanceObject(
                            Utils.createVariableName(instantiate.name),
                            Utils.createVariableName(instantiate.name, "inst"),
                            Utils.createTypeName(definition.name)
                    )

                    instanceObject.mappings.add(MappingObject(
                            "start",
                            Utils.createVariableName(instanceObject.id, "start")
                    ))

                    instanceObject.mappings.add(MappingObject(
                            "finish",
                            Utils.createVariableName(instanceObject.id, "finish")
                    ))

                    val runtimeMappingObject = RuntimeMappingObject()

                    val runtimeMappingProcess = RuntimeMappingProcessObject(
                            Utils.createVariableName(instanceObject.name, "proc"),
                            Utils.createVariableName(instanceObject.id, "start"),
                            Utils.createVariableName(instanceObject.id, "finish"),
                            Utils.createVariableName(instanceObject.name, "proc", "done"),
                            Utils.createVariableName(instanceObject.name, "proc", "start")
                    )

                    runtimeMappingProcess.variables.add(VariableObject.create(Variable(Utils.createVariableName(instanceObject.id, "start"), VariableType.BOOLEAN, Locality.INTERNAL)))
                    runtimeMappingProcess.variables.add(VariableObject.create(Variable(Utils.createVariableName(instanceObject.id, "finish"), VariableType.BOOLEAN, Locality.INTERNAL)))

                    if(definition is HybridAutomata) {
                        val variableObjectIn = VariableObject.create(Variable("state", VariableType.INTEGER, Locality.EXTERNAL_INPUT), runtimeParametrisation = true)
                        variableObjectIn.type = "integer range 0 to ${definition.locations.size-1}"
                        component.variables.add(variableObjectIn)

                        instanceObject.mappings.add(MappingObject(
                                Utils.createVariableName(variableObjectIn.io),
                                Utils.createVariableName(instanceObject.id, "state", "in")
                        ))

                        runtimeMappingProcess.variables.add(VariableObject.create(Variable(Utils.createVariableName(instanceObject.id, "state", "in"), VariableType.INTEGER, Locality.INTERNAL)))
                        runtimeMappingProcess.variables.last().type = variableObjectIn.type

                        val variableObjectOut = VariableObject.create(Variable("state", VariableType.INTEGER, Locality.EXTERNAL_OUTPUT), runtimeParametrisation = true)
                        variableObjectOut.type = variableObjectIn.type
                        component.variables.add(variableObjectOut)

                        instanceObject.mappings.add(MappingObject(
                                Utils.createVariableName(variableObjectOut.io),
                                Utils.createVariableName(instanceObject.id, "state", "out")
                        ))

                        runtimeMappingProcess.variables.add(VariableObject.create(Variable(Utils.createVariableName(instanceObject.id, "state", "out"), VariableType.INTEGER, Locality.INTERNAL)))
                        runtimeMappingProcess.variables.last().type = variableObjectIn.type

                        val localSignal = VariableObject.create(Variable(Utils.createVariableName(name, "state"), VariableType.INTEGER, Locality.INTERNAL))
                        localSignal.initialValue = definition.locations.indexOfFirst { it.name == definition.init.state }.toString()
                        localSignal.initialValueString = definition.init.state
                        localSignal.type = variableObjectIn.type

                        rootItem.variables.add(localSignal)

                        runtimeMappingObject.mappingsIn.add(MappingObject(
                                Utils.createVariableName(instanceObject.id, "state", "in"),
                                localSignal.signal
                        ))

                        runtimeMappingObject.mappingsOut.add(MappingObject(
                                localSignal.signal,
                                Utils.createVariableName(instanceObject.id, "state", "out")
                        ))

                        signalNameMap["${name}.state"] = localSignal.signal
                    }

                    for (variable in definition.variables.sortedWith(compareBy({ it.locality }, { it.type }))) {
                        if (variable.canBeDelayed()) {
                            throw NotImplementedError("Delayed variables are currently not supported in VHDL Generation")
                        }

                        val defaultValue = if (instance.parameters.containsKey(variable.name)) {
                            instance.parameters[variable.name]
                        } else if (definition is HybridAutomata && definition.init.valuations.containsKey(variable.name)) {
                            definition.init.valuations[variable.name]
                        } else {
                            variable.defaultValue
                        }

                        if(variable.locality == Locality.EXTERNAL_OUTPUT || variable.locality == Locality.INTERNAL) {
                            val variableObjectIn = VariableObject.create(variable.copy(defaultValue = defaultValue, locality = Locality.EXTERNAL_INPUT), runtimeParametrisation = true)
                            component.variables.add(variableObjectIn)

                            instanceObject.mappings.add(MappingObject(
                                    Utils.createVariableName(variableObjectIn.io),
                                    Utils.createVariableName(instanceObject.id, variable.name, "in")
                            ))

                            runtimeMappingProcess.variables.add(VariableObject.create(Variable(Utils.createVariableName(instanceObject.id, variable.name, "in"), variable.type, Locality.INTERNAL)))

                            val variableObjectOut = VariableObject.create(variable.copy(defaultValue = defaultValue, locality = Locality.EXTERNAL_OUTPUT), runtimeParametrisation = true)
                            component.variables.add(variableObjectOut)

                            instanceObject.mappings.add(MappingObject(
                                    Utils.createVariableName(variableObjectOut.io),
                                    Utils.createVariableName(instanceObject.id, variable.name, "out")
                            ))

                            runtimeMappingProcess.variables.add(VariableObject.create(Variable(Utils.createVariableName(instanceObject.id, variable.name, "out"), variable.type, Locality.INTERNAL)))
                        }
                        else {
                            val variableObject = VariableObject.create(variable.copy(defaultValue = defaultValue), runtimeParametrisation = true)
                            component.variables.add(variableObject)

                            instanceObject.mappings.add(MappingObject(
                                    Utils.createVariableName(variableObject.io),
                                    Utils.createVariableName(instanceObject.id, variable.name)
                            ))

                            runtimeMappingProcess.variables.add(VariableObject.create(Variable(Utils.createVariableName(instanceObject.id, variable.name), variable.type, Locality.INTERNAL)))
                        }

                        val localSignal = VariableObject.create(Variable(Utils.createVariableName(name, variable.name), variable.type, Locality.INTERNAL, defaultValue, variable.delayableBy))
                        if(variable.locality == Locality.PARAMETER)
                            localSignal.locality = Locality.PARAMETER.getTextualName()

                        rootItem.variables.add(localSignal)

                        if(variable.locality == Locality.EXTERNAL_INPUT || variable.locality == Locality.PARAMETER) {
                            runtimeMappingObject.mappingsIn.add(MappingObject(
                                    Utils.createVariableName(instanceObject.id, variable.name),
                                    localSignal.signal
                            ))
                        }
                        else if(variable.locality == Locality.EXTERNAL_OUTPUT || variable.locality == Locality.INTERNAL) {
                            runtimeMappingObject.mappingsIn.add(MappingObject(
                                    Utils.createVariableName(instanceObject.id, variable.name, "in"),
                                    localSignal.signal
                            ))

                            runtimeMappingObject.mappingsOut.add(MappingObject(
                                    localSignal.signal,
                                    Utils.createVariableName(instanceObject.id, variable.name, "out")
                            ))
                        }

                        signalNameMap["${name}.${variable.name}"] = localSignal.signal
                    }

                    if (!generated.contains(instantiate.definition)) {
                        generated.add(instantiate.definition)

                        rootItem.components.add(component)

                        rootItem.instances.add(instanceObject)

                        rootItem.runtimeMappingProcesses.add(runtimeMappingProcess)

                        processDoneEquation = if(processDoneEquation == null)
                            ParseTreeItem.generate(Utils.createVariableName(instanceObject.name, "proc", "done"))
                        else
                            And(processDoneEquation, ParseTreeItem.generate(Utils.createVariableName(instanceObject.name, "proc", "done")))
                    }

                    rootItem.runtimeMappingProcesses.first { it.name.equals(Utils.createVariableName(instanceObject.name, "proc")) }.runtimeMappings.add(runtimeMappingObject)
                }
            }

            if(processDoneEquation != null)
                rootItem.runtimeProcessDoneSignal = Utils.generateCodeForParseTreeItem(processDoneEquation)
        }

        for((destination, value) in item.ioMapping) {
            if(destination.automata.isEmpty()) {
                rootItem.mappings.add(MappingObject(
                        Utils.createVariableName(signalNameMap[destination.variable] ?: destination.variable),
                        Utils.generateCodeForParseTreeItem(value, Utils.PrefixData("", signalNameMap))
                ))
            }
            else {
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

    data class NetworkFileContext(val map: MutableMap<String, Any?>) {
        var config: Configuration by map
        var item: NetworkFileObject by map

        constructor(config: Configuration, item: NetworkFileObject) : this(
                mutableMapOf(
                        "config" to config,
                        "item" to item
                )
        )
    }

    data class NetworkFileObject(
            var name: String,
            var parameters: MutableList<VariableObject> = ArrayList(),
            var variables: MutableList<VariableObject> = ArrayList(),
            var components: MutableList<ComponentObject> = ArrayList(),
            var instances: MutableList<InstanceObject> = ArrayList(),
            var mappings: MutableList<MappingObject> = ArrayList(),
            var runtimeMappingProcesses: MutableList<RuntimeMappingProcessObject> = ArrayList(),
            var runtimeProcessDoneSignal: String = "true"
    )

    data class ComponentObject(
            var name: String,
            var parameters: MutableList<VariableObject> = ArrayList(),
            var variables: MutableList<VariableObject> = ArrayList()
    )

    data class InstanceObject(
            var name: String,
            var id: String,
            var type: String,
            var parameters: MutableList<MappingObject> = ArrayList(),
            var mappings: MutableList<MappingObject> = ArrayList()
    )

    data class MappingObject(
            var left: String,
            var right: String
    )

    data class RuntimeMappingProcessObject(
            var name: String,
            var startSignal: String,
            var finishSignal: String,
            var processDoneSignal: String,
            var processStartSignal: String,
            var variables: MutableList<VariableObject> = ArrayList(),
            var runtimeMappings: MutableList<RuntimeMappingObject> = ArrayList()
    )

    data class RuntimeMappingObject(
            var mappingsIn: MutableList<MappingObject> = ArrayList(),
            var mappingsOut: MutableList<MappingObject> = ArrayList()
    )
}