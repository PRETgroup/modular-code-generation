package me.nallen.modularCodeGeneration.codeGen.c

import com.hubspot.jinjava.Jinjava
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.hybridAutomata.HybridItem
import me.nallen.modularCodeGeneration.hybridAutomata.HybridNetwork
import java.util.*

import me.nallen.modularCodeGeneration.codeGen.c.MakefileGenerator.MakeObject.CompileObject as CompileObject
import me.nallen.modularCodeGeneration.codeGen.c.MakefileGenerator.MakeObject.SubCallObject as SubCallObject

/**
 * The class that contains methods to do with the generation of the MakeFile for the network
 */
object MakefileGenerator {
    /**
     * Generates a string that represents the Makefile for the network. The final generated program will be named by the
     * provided networkName.
     */
    fun generate(item: HybridItem, config: Configuration, isRoot: Boolean): String {
        val jinjava = Jinjava()

        val template = this::class.java.classLoader.getResource("templates/c/Makefile").readText()

        // Get all the items to compile
        val objects = ArrayList<MakeObject>()

        // The root Makefile (for the runnable) has a slightly different look
        if(isRoot) {
            // We still need to compile the root item, which changes if it's a Network or Automata
            val deliminatedName = Utils.createFileName(item.name)

            // Depending on if it's a Network or Automata we'll do something different
            if (item is HybridNetwork) {
                // If it's a Network, then we'll recursively call the Makefile for that network
                objects.add(
                        SubCallObject(
                                item.name,
                                "$deliminatedName.a",
                                Utils.createFolderName(item.name, "Network")
                        )
                )
            } else {
                // Otherwise it must be an Automata so we just add a normal compile command for it
                objects.add(
                        CompileObject(
                                item.name,
                                "$deliminatedName.o",
                                listOf("$deliminatedName.c"),
                                listOf("$deliminatedName.h")
                        )
                )
            }

            // Create the compile command for the runnable main file
            objects.add(
                    CompileObject(
                            "runnable",
                            "runnable.o",
                            listOf("runnable.c"),
                            listOf()
                    )
            )
        }
        else {
            // We can only generate code if this is a HybridNetwork
            if(item is HybridNetwork) {
                // Depending on the parametrisation method, we'll do things slightly differently
                if(config.compileTimeParametrisation) {
                    // Compile time parametrisation means compiling each instantiate
                    for ((_, instance) in item.instances) {
                        // Get the item that we're generating a compile command for
                        val instantiate = item.getInstantiateForInstantiateId(instance.instantiate)
                        val definition = item.getDefinitionForInstantiateId(instance.instantiate)
                        if (instantiate != null && definition != null) {
                            // Generate the file name that we'll be looking for
                            val deliminatedName = Utils.createFileName(instantiate.name)

                            // Generated the folder name that we'll be looking for
                            val subfolder = if (definition.name.equals(item.name, true)) {
                                definition.name + " Files"
                            } else {
                                definition.name
                            }
                            val deliminatedFolder = Utils.createFolderName(subfolder)

                            // Depending on if it's a Network or Automata we'll do something different
                            if (definition is HybridNetwork) {
                                // If it's a Network, then we'll recursively call the Makefile for that network
                                objects.add(
                                        SubCallObject(
                                                instantiate.name,
                                                "$deliminatedName.a",
                                                deliminatedFolder
                                        )
                                )
                            } else {
                                // Otherwise it must be an Automata so we just add a normal compile command for it
                                objects.add(
                                        CompileObject(
                                                instantiate.name,
                                                "$deliminatedName.o",
                                                listOf("$deliminatedFolder/$deliminatedName.c"),
                                                listOf("$deliminatedFolder/$deliminatedName.h")
                                        )
                                )
                            }
                        }
                    }
                }
                else {
                    // We only want to generate each definition once, so keep a track of them
                    val generated = ArrayList<UUID>()
                    for ((_, instance) in item.getAllInstances()) {
                        // Get the item that we're generating a compile command for
                        val instantiate = item.getInstantiateForInstantiateId(instance.instantiate, true)
                        if (instantiate != null) {
                            // Check if we've seen this type before
                            if (!generated.contains(instantiate.definition)) {
                                // If we haven't seen it, keep track of it
                                generated.add(instantiate.definition)

                                // Generate the file name that we'll be looking for
                                val deliminatedName = Utils.createFileName(instantiate.name)

                                objects.add(
                                        CompileObject(
                                                instantiate.name,
                                                "$deliminatedName.o",
                                                listOf("$deliminatedName.c"),
                                                listOf("$deliminatedName.h")
                                        )
                                )
                            }
                        }
                    }
                }
            }

            // Generate the file name for the main file of this network
            val deliminatedName = Utils.createFileName(item.name)

            objects.add(
                    CompileObject(
                            item.name,
                            "$deliminatedName.o",
                            listOf("$deliminatedName.c"),
                            listOf("$deliminatedName.h")
                    )
            )
        }

        // Create the context
        val context = MakefileContext(
                config,
                Utils.createFileName(item.name) + if(!isRoot) { ".a" } else { "" },
                if(isRoot) { "link" } else { "archive" },
                objects.sortedWith(compareBy({ it.type }, { it.name }))
        )

        val res = jinjava.renderForResult(template, context.map)

        // And generate!
        return res.output
    }


    data class MakefileContext(val map: MutableMap<String, Any?>) {
        var config: Configuration by map
        var target: String by map
        var targetMethod: String by map
        var objects: List<MakeObject> by map

        constructor(config: Configuration, target: String, targetMethod: String, objects: List<MakeObject>) : this(
                mutableMapOf(
                        "config" to config,
                        "target" to target,
                        "targetMethod" to targetMethod,
                        "objects" to objects
                )
        )
    }

    sealed class MakeObject(var type: String, open var name: String = "") {
        data class CompileObject(override var name: String,
                                 var outputFile: String,
                                 var sources: List<String>,
                                 var dependencies: List<String>
        ) : MakeObject("compile", name)

        data class SubCallObject(override var name: String,
                                 var outputFile: String,
                                 var outputDir: String
        ) : MakeObject("subcall", name)
    }
}