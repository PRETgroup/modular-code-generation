package me.nallen.modularCodeGeneration.codeGen.c

import com.hubspot.jinjava.Jinjava
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.hybridAutomata.*
import java.util.*

/**
 * The class that contains methods to do with the generation of Header Files for the Hybrid Item
 */
object HFileGenerator {
    /**
     * Generates a string that represents the Header File for the given Hybrid Item
     */
    fun generate(item: HybridItem, config: Configuration = Configuration()): String {
        val jinjava = Jinjava()

        val template = this::class.java.classLoader.getResource("templates/c/item.h").readText()

        // Generate data about the root item
        val rootItem = HFileObject(item.name, Utils.createMacroName(item.name), Utils.createTypeName(item.name),
                Utils.createFunctionName(item.name, "Run"), Utils.createFunctionName(item.name, "Init"),
                Utils.createFunctionName(item.name, "Parametrise"))

        if(item is HybridNetwork) {
            // Different logic depending on the parametrisation method
            if(config.compileTimeParametrisation) {
                // If it's compile time then we need to include a type for each instantiation
                for((name, instance) in item.instances) {
                    val instantiate = item.getInstantiateForInstantiateId(instance.instantiate)
                    val definition = item.getDefinitionForInstantiateId(instance.instantiate)
                    if(instantiate != null && definition != null) {
                        // And the includes will be in a sub-directory of the instantiate type
                        val subfolder = if(definition.name.equals(item.name, true)) { definition.name + " Files" } else { definition.name }
                        val include = "${Utils.createFolderName(subfolder)}/${Utils.createFileName(instantiate.name)}.h"

                        rootItem.children.add(ChildObject(include, Utils.createTypeName(instantiate.name), Utils.createVariableName(name, "data")))
                    }
                }
            }
            else {
                // Otherwise it's run time
                for((name, instance) in item.instances) {
                    val instantiate = item.getInstantiateForInstantiateId(instance.instantiate)
                    if(instantiate != null) {
                        val include = "${Utils.createFileName(instantiate.name)}.h"

                        rootItem.children.add(ChildObject(include, Utils.createTypeName(instantiate.name), Utils.createVariableName(name, "data")))
                    }
                }

                rootItem.children = rootItem.children.sortedWith(compareBy({ it.type }, { it.variable })).toMutableList()
            }
        }

        if(item is HybridAutomata) {
            rootItem.enumName = Utils.createTypeName(item.name, "States")
            for((name) in item.locations) {
                // Create an entry in the num for it
                rootItem.locations.add(Utils.createMacroName(item.name, name))
            }
        }

        for(variable in item.variables.sortedWith(compareBy({ it.locality }, { it.type }))) {
            val delayedObject = if(variable.canBeDelayed()) {
                rootItem.hasDelayed = true
                DelayedObject(
                        Utils.createTypeName("Delayable", Utils.generateCType(variable.type)),
                        Utils.createVariableName(variable.name, "delayed")
                )
            }
            else {
                null
            }

            val variableObject = VariableObject(
                    variable.locality.getTextualName(),
                    Utils.generateCType(variable.type),
                    Utils.createVariableName(variable.name),
                    delayedObject
            )

            rootItem.variables.add(variableObject)
        }

        // Create the context
        val context = HFileContext(
                config,
                rootItem
        )

        val res = jinjava.renderForResult(template, context.map)

        // And generate!
        return res.output
    }

    data class HFileContext(val map: MutableMap<String, Any?>) {
        var config: Configuration by map
        var item: HFileObject by map

        constructor(config: Configuration, item: HFileObject) : this(
                mutableMapOf(
                        "config" to config,
                        "item" to item
                )
        )
    }

    data class HFileObject(
            var name: String,
            var macro: String,
            var type: String,
            var runFunction: String,
            var initFunction: String,
            var paramFunction: String,
            var hasDelayed: Boolean = false,
            var variables: MutableList<VariableObject> = ArrayList(),
            var enumName: String = "",
            var locations: MutableList<String> = ArrayList(),
            var children: MutableList<ChildObject> = ArrayList()
    )

    data class VariableObject(
            var locality: String,
            var type: String,
            var variable: String,
            var delayed: DelayedObject? = null
    )

    data class DelayedObject(
            var type: String,
            var variable: String
    )

    data class ChildObject(
            var include: String,
            var type: String,
            var variable: String
    )
}