package me.nallen.modularCodeGeneration.codeGen.vhdl

import com.hubspot.jinjava.Jinjava
import me.nallen.modularCodeGeneration.codeGen.CodeGenManager
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.ParametrisationMethod
import me.nallen.modularCodeGeneration.hybridAutomata.*
import me.nallen.modularCodeGeneration.hybridAutomata.Locality
import me.nallen.modularCodeGeneration.parseTree.*
import me.nallen.modularCodeGeneration.parseTree.Variable
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.sign

/**
 * The class that contains methods to do with the generation of Header Files for the Hybrid Item
 */
object NetworkGenerator {
    /**
     * Generates a string that represents the Header File for the given Hybrid Item
     */
    fun generate(item: HybridNetwork, config: Configuration = Configuration()): String {
        val jinjava = Jinjava()

        val template = this::class.java.classLoader.getResource("templates/vhdl/network.vhdl").readText()

        // Generate data about the root item
        val rootItem = NetworkFileObject(item.name)

        val signalNameMap = HashMap<String, String>()

        for(variable in item.variables.sortedWith(compareBy({ it.locality }, { it.type }))) {
            if(variable.canBeDelayed()) {
                throw NotImplementedError("Delayed variables are currently not supported in VHDL Generation")
            }

            val default: ParseTreeItem? = if(variable.locality == Locality.PARAMETER) {
                variable.defaultValue
            } else {
                null
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
                    variable.locality.getShortName().toLowerCase(),
                    Utils.generateVHDLType(variable.type),
                    Utils.createVariableName(variable.name, variable.locality.getShortName()),
                    Utils.createVariableName(variable.name),
                    Utils.createVariableName(variable.name, "update"),
                    defaultValue.toString(),
                    defaultValueString
            )

            if(variable.locality == Locality.EXTERNAL_INPUT || variable.locality == Locality.EXTERNAL_OUTPUT)
                signalNameMap[variable.name] = variableObject.io
            else
                signalNameMap[variable.name] = variableObject.signal

            rootItem.variables.add(variableObject)
        }

        // Depending on the parametrisation method, we'll do things slightly differently
        if(config.parametrisationMethod == ParametrisationMethod.COMPILE_TIME) {
            // Compile time parametrisation means each sub-item will have its own component
            for((_, instance) in item.instances) {
                // Get the instance of the item we want to generate
                val instantiate = item.getInstantiateForInstantiateId(instance.instantiate)
                if(instantiate != null) {
                    val definition = item.getDefinitionForDefinitionId(instantiate.definition) ?: throw IllegalArgumentException("Unable to find base machine ${instantiate.name} to instantiate!")

                    val component = ComponentObject(
                            Utils.createTypeName(instantiate.name)
                    )

                    val instanceObject = InstanceObject(
                            Utils.createVariableName(instantiate.name),
                            Utils.createTypeName(instantiate.name)
                    )

                    for(variable in definition.variables.sortedWith(compareBy({ it.locality }, { it.type }))) {
                        if(variable.canBeDelayed()) {
                            throw NotImplementedError("Delayed variables are currently not supported in VHDL Generation")
                        }

                        val default: ParseTreeItem? = if(variable.locality == Locality.PARAMETER) {
                            variable.defaultValue
                        } else {
                            null
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
                                variable.locality.getShortName().toLowerCase(),
                                Utils.generateVHDLType(variable.type),
                                Utils.createVariableName(variable.name, variable.locality.getShortName()),
                                Utils.createVariableName(variable.name),
                                Utils.createVariableName(variable.name, "update"),
                                defaultValue.toString(),
                                defaultValueString
                        )

                        component.variables.add(variableObject)

                        if(variable.locality == Locality.EXTERNAL_OUTPUT || variable.locality == Locality.EXTERNAL_INPUT) {
                            val localSignal = VariableObject(
                                    Locality.INTERNAL.getTextualName(),
                                    Locality.INTERNAL.getShortName().toLowerCase(),
                                    Utils.generateVHDLType(variable.type),
                                    Utils.createVariableName(instanceObject.name, variable.name, variable.locality.getShortName()),
                                    Utils.createVariableName(instanceObject.name, variable.name),
                                    Utils.createVariableName(instanceObject.name, variable.name, "update"),
                                    defaultValue.toString(),
                                    defaultValueString
                            )

                            rootItem.variables.add(localSignal)
                            instanceObject.mappings.add(MappingObject(
                                    Utils.createVariableName(variable.name, variable.locality.getShortName()),
                                    Utils.createVariableName(instanceObject.name, variable.name)
                            ))

                            signalNameMap["${instanceObject.name}.${variable.name}"] = localSignal.signal
                        }
                    }

                    rootItem.components.add(component)
                    rootItem.instances.add(instanceObject)
                }
            }
        }
        else  {
            throw NotImplementedError("Not yet implemented")
        }

        for((destination, value) in item.ioMapping) {
            println(signalNameMap)
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
            var variables: MutableList<VariableObject> = ArrayList(),
            var components: MutableList<ComponentObject> = ArrayList(),
            var instances: MutableList<InstanceObject> = ArrayList(),
            var mappings: MutableList<MappingObject> = ArrayList()
    )

    data class VariableObject(
            var locality: String,
            var direction: String,
            var type: String,
            var io: String,
            var signal: String,
            var variable: String,
            var initialValue: String,
            var initialValueString: String
    )

    data class ComponentObject(
            var name: String,
            var variables: MutableList<VariableObject> = ArrayList()
    )

    data class InstanceObject(
            var name: String,
            var type: String,
            var mappings: MutableList<MappingObject> = ArrayList()
    )

    data class MappingObject(
            var left: String,
            var right: String
    )
}