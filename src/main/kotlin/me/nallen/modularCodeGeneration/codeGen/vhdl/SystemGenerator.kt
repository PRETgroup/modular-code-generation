package me.nallen.modularCodeGeneration.codeGen.vhdl

import com.hubspot.jinjava.Jinjava
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.vhdl.Utils.VariableObject
import me.nallen.modularCodeGeneration.hybridAutomata.HybridItem
import me.nallen.modularCodeGeneration.hybridAutomata.Locality
import me.nallen.modularCodeGeneration.hybridAutomata.Variable

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

            val variableObject = VariableObject.create(variable)

            variables.add(variableObject)

            if(variable.locality == Locality.PARAMETER)
                component.parameters.add(variableObject)
            else
                component.variables.add(variableObject)

            if(variable.locality == Locality.EXTERNAL_OUTPUT || variable.locality == Locality.EXTERNAL_INPUT) {
                val localSignal = VariableObject.create(Variable(Utils.createVariableName(instanceObject.name, variable.name), variable.type, Locality.INTERNAL, variable.defaultValue, variable.delayableBy))

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