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

        // Create the base component
        val component = ComponentObject(
                Utils.createTypeName(item.name)
        )

        // We have a single instance for the system
        val instanceObject = InstanceObject(
                Utils.createVariableName(item.name),
                Utils.createVariableName(item.name, "inst"),
                Utils.createTypeName(item.name)
        )

        // Now we need to go through each top level variable of the system
        for(variable in item.variables.sortedWith(compareBy({ it.locality }, { it.type }))) {
            // Delayed variables are not currently supported in VHDL, so we currently error out
            if(variable.canBeDelayed()) {
                throw NotImplementedError("Delayed variables are currently not supported in VHDL Generation")
            }

            // Now let's create the variable object and add it
            val variableObject = VariableObject.create(variable)

            variables.add(variableObject)

            // We need to add either the signal or the parameter to the base component
            // There shouldn't be anything with parameters at the top level, but just in case we have this code
            if(variable.locality == Locality.PARAMETER)
                component.parameters.add(variableObject)
            else
                component.variables.add(variableObject)

            // If the variable is an external signal then we need to create a local signal that things get mapped to
            if(variable.locality == Locality.EXTERNAL_OUTPUT || variable.locality == Locality.EXTERNAL_INPUT) {
                // Create a VariableObject for the local signal with a locality of internal
                val localSignal = VariableObject.create(Variable(Utils.createVariableName(instanceObject.name, variable.name), variable.type, Locality.INTERNAL, variable.defaultValue, variable.delayableBy))

                // Add the local signal to the system, and a mapping from that to the instance
                variables.add(localSignal)
                instanceObject.mappings.add(MappingObject(
                        Utils.createVariableName(variable.name, variable.locality.getShortName()),
                        Utils.createVariableName(instanceObject.name, variable.name)
                ))

                // Now we need to make the mapping to the external signal as applicable
                if(variable.locality == Locality.EXTERNAL_INPUT) {
                    // If it's an input
                    mappings.add(MappingObject(
                            localSignal.signal,
                            variableObject.io
                    ))
                }
                else {
                    // Or if it's an output
                    mappings.add(MappingObject(
                            variableObject.io,
                            localSignal.signal
                    ))
                }
            }
        }

        // Now we can create the root item
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

    /**
     * The class which stores the context for the base system file
     */
    data class SystemFileContext(val map: MutableMap<String, Any?>) {
        // Config file for the generation
        var config: Configuration by map

        // Information about the file to be generated
        var item: SystemFileObject by map

        constructor(config: Configuration, item: SystemFileObject) : this(
                mutableMapOf(
                        "config" to config,
                        "item" to item
                )
        )
    }

    /**
     * The actual information for the base system file that we need to generate
     */
    data class SystemFileObject(
            // The name of the overally system
            var name: String,

            // The component definition of the top level file
            var component: ComponentObject,

            // The instante that instantiates the above component
            var instance: InstanceObject,

            // A list of variables that are part of the system file
            var variables: MutableList<VariableObject> = ArrayList(),

            // Any signal mappings that need to be performed between I/O and local signals
            var mappings: MutableList<MappingObject> = ArrayList()
    )

    /**
     * Contains information about a Component declaration
     */
    data class ComponentObject(
            // The name of the component
            var name: String,

            // The list of parameters the component has
            var parameters: MutableList<VariableObject> = ArrayList(),

            // The list of variables the component has
            var variables: MutableList<VariableObject> = ArrayList()
    )

    /**
     * Contains information about an Instance declaration
     */
    data class InstanceObject(
            // The name of the instance
            var name: String,

            // The ID of the instance so it can be defined if needed
            var id: String,

            // The type that the instance instantiates
            var type: String,

            // The mappings used for parameters of the instance
            var parameters: MutableList<MappingObject> = ArrayList(),

            // The mappings of I/O signals of the instance to local signals
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
}