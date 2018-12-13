package me.nallen.modularCodeGeneration.codeGen.vhdl

import com.hubspot.jinjava.Jinjava
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.hybridAutomata.HybridItem
import me.nallen.modularCodeGeneration.hybridAutomata.Locality
import me.nallen.modularCodeGeneration.parseTree.ParseTreeItem
import me.nallen.modularCodeGeneration.parseTree.evaluate

/**
 * The class that contains methods to do with the generation of the main runnable file for the system
 */
object SystemGenerator {
    /**
     * Generates a string that represents the main runnable file for the system.
     */
    fun generate(item: HybridItem, config: Configuration = Configuration()): String {
        val jinjava = Jinjava()

        val template = this::class.java.classLoader.getResource("templates/vhdl/system.vhdl").readText()

        // Generate data about the system
        val variables = ArrayList<VariableObject>()
        val mappings = ArrayList<MappingObject>()

        val component = ComponentObject(
                Utils.createTypeName(item.name)
        )

        val instanceObject = InstanceObject(
                Utils.createVariableName(item.name),
                Utils.createVariableName(item.name, "inst"),
                Utils.createTypeName(item.name)
        )

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
                defaultValue = try {
                    default.evaluate()
                } catch(e: IllegalArgumentException) {
                    Utils.generateCodeForParseTreeItem(default)
                }
                defaultValueString = default.getString()

                if(defaultValue is Boolean)
                    defaultValue = if(defaultValue) { "true" } else { "false" }
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

            variables.add(variableObject)

            if(variable.locality == Locality.PARAMETER)
                component.parameters.add(variableObject)
            else
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

                variables.add(localSignal)
                instanceObject.mappings.add(MappingObject(
                        Utils.createVariableName(variable.name, variable.locality.getShortName()),
                        Utils.createVariableName(instanceObject.name, variable.name)
                ))

                if(variable.locality == Locality.EXTERNAL_INPUT) {
                    mappings.add(MappingObject(
                            localSignal.signal,
                            variableObject.io
                    ))
                }
                else {
                    mappings.add(MappingObject(
                            variableObject.io,
                            localSignal.signal
                    ))
                }
            }
        }

        val rootItem = SystemFileObject("system", component, instanceObject, variables, mappings)

        // Create the context
        val context = SystemFileContext(
                config,
                rootItem
        )

        val res = jinjava.renderForResult(template, context.map)

        // And generate!
        return res.output
    }

    data class SystemFileContext(val map: MutableMap<String, Any?>) {
        var config: Configuration by map
        var item: SystemFileObject by map

        constructor(config: Configuration, item: SystemFileObject) : this(
                mutableMapOf(
                        "config" to config,
                        "item" to item
                )
        )
    }

    data class SystemFileObject(
            var name: String,
            var component: ComponentObject,
            var instance: InstanceObject,
            var variables: MutableList<VariableObject> = ArrayList(),
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
}