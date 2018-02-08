package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.CodeGenManager
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.ParametrisationMethod
import me.nallen.modularCodeGeneration.codeGen.c.Utils.createFileName
import me.nallen.modularCodeGeneration.hybridAutomata.AutomataInstance
import me.nallen.modularCodeGeneration.hybridAutomata.HybridAutomata
import me.nallen.modularCodeGeneration.hybridAutomata.HybridItem
import me.nallen.modularCodeGeneration.hybridAutomata.HybridNetwork
import me.nallen.modularCodeGeneration.parseTree.VariableType
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

/**
 * The class that contains methods to do with the generation of C code for a Hybrid Network.
 *
 * All generated files and the generated code itself will follow proper C style conventions.
 */
class CCodeGenerator {
    companion object {
        const val RUNNABLE = "runnable.c"
        const val MAKEFILE = "Makefile"
        const val CONFIG_FILE = "config.h"
        const val DELAYABLE_HEADER = "delayable.h"

        // We need to keep track of the variables we need to delay
        private val delayedTypes = ArrayList<VariableType>()

        /**
         * Generate C code for the FSM that represents a Hybrid Automata. The code will be placed into the provided
         * directory, overwriting any contents that may already exist.
         */
        private fun generateItem(item: HybridItem, dir: String, config: Configuration) {
            val outputDir = File(dir)

            // If the directory doesn't already exist, we want to create it
            if(!outputDir.exists())
                outputDir.mkdirs()

            // Generate the Header File
            File(outputDir, "${Utils.createFileName(item.name)}.h").writeText(HFileGenerator.generate(item, config))
            // Generate the Source File
            File(outputDir, "${Utils.createFileName(item.name)}.c").writeText(CFileGenerator.generate(item, config))
        }

        /**
         * Generate C code for the overall runnable that represents a Hybrid Network. The code will be placed into the
         * provided directory, overwriting any contents that may already exist.
         */
        private fun generateRunnable(item: HybridItem, dir: String, config: Configuration = Configuration()) {
            val outputDir = File(dir)

            // If the directory doesn't already exist, we want to create it
            if(!outputDir.exists())
                outputDir.mkdirs()

            // Generate the Runnable File
            File(outputDir, RUNNABLE).writeText(RunnableGenerator.generate(item, config))
        }

        /**
         * Generate a Makefile for the overall program that represents a Hybrid Network. The code will be placed into
         * the provided directory, overwriting any contents that may already exist.
         */
        private fun generateMakefile(item: HybridItem, dir: String, config: Configuration, isRoot: Boolean = false) {
            val outputDir = File(dir)

            // If the directory doesn't already exist, we want to create it
            if(!outputDir.exists())
                outputDir.mkdirs()

            // Generate the Makefile
            File(outputDir, MAKEFILE).writeText(MakefileGenerator.generate(item, config, isRoot))
        }

        /**
         * Generate a configuration file for the overall program. The code will be placed into the provided directory,
         * overwriting any contents that may already exist.
         */
        private fun generateConfigFile(dir: String, config: Configuration = Configuration()) {
            val outputDir = File(dir)

            // If the directory doesn't already exist, we want to create it
            if(!outputDir.exists())
                outputDir.mkdirs()

            // Generate the content
            val content = StringBuilder()

            // Represent most of the config properties as #defines in the C
            // Execution Settings
            content.appendln("#define STEP_SIZE ${config.execution.stepSize}")
            content.appendln("#define SIMULATION_TIME ${config.execution.simulationTime}")

            // Logging Settings
            content.appendln("#define ENABLE_LOGGING ${if(config.logging.enable) 1 else 0}")
            content.appendln("#define LOGGING_FILE \"${config.logging.file}\"")
            content.appendln("#define LOGGING_INTERVAL ${config.logging.interval ?: config.execution.stepSize}")

            // And write the content
            File(outputDir, CONFIG_FILE).writeText(content.toString())
        }

        /**
         * Generate Delayable files for the requested types. These files are used to allow for signals to be delayed and
         * define both the struct that holds all the data, and the methods for accessing data. The code will be placed
         * into the provided directory, overwriting any contents that may already exist.
         */
        private fun generateDelayableFiles(types: List<VariableType>, dir: String, config: Configuration = Configuration()) {
            val outputDir = File(dir)

            // If the directory doesn't already exist, we want to create it
            if(!outputDir.exists())
                outputDir.mkdirs()

            // Generate the content
            val content = StringBuilder()

            // Create the guard for the file
            content.appendln("#ifndef DELAYABLE_H_")
            content.appendln("#define DELAYABLE_H_")
            content.appendln()

            // We depend on stdlib and string.h
            content.appendln("#include <stdlib.h>")
            content.appendln("#include <string.h>")
            content.appendln()

            // If we're generating for a boolean type
            if(types.contains(VariableType.BOOLEAN)) {
                // We need to create the type for bool, and true / false
                content.appendln("typedef int bool;")
                content.appendln("#define false 0")
                content.appendln("#define true 1")
                content.appendln()
            }

            // Include the config file so we have access to step size
            content.appendln("#include \"$CONFIG_FILE\"")
            content.appendln()

            // For each type we need to make delayable
            for(type in types) {
                // Create the struct, which includes a buffer, max length, and current index
                content.appendln("// Delayable struct for type ${Utils.generateCType(type)}")
                content.appendln("typedef struct {")
                content.appendln("${config.getIndent(1)}${Utils.generateCType(type)} *buffer;")
                content.appendln("${config.getIndent(1)}unsigned int index;")
                content.appendln("${config.getIndent(1)}unsigned int max_length;")
                content.appendln("} ${Utils.createTypeName("Delayable", Utils.generateCType(type))};")
                content.appendln()

                // Create the initialisation function, which instantiates the buffer, sets the max length, and resets
                // the index to 0
                content.appendln("// Initialisation function")
                content.appendln("static inline void ${Utils.createFunctionName("Delayable", Utils.generateCType(type), "Init")}(${Utils.createTypeName("Delayable", Utils.generateCType(type))}* me, double max_delay) {")
                content.appendln("${config.getIndent(1)}me->index = 0;")
                content.appendln("${config.getIndent(1)}me->max_length = (unsigned int) (max_delay / STEP_SIZE);")
                content.appendln("${config.getIndent(1)}me->buffer = malloc(sizeof(${Utils.generateCType(type)}) * me->max_length);")
                content.appendln("}")
                content.appendln()

                // Create the add function, which will add an item to the buffer and roll around the index if needed
                content.appendln("// Add function")
                content.appendln("static inline void ${Utils.createFunctionName("Delayable", Utils.generateCType(type), "Add")}(${Utils.createTypeName("Delayable", Utils.generateCType(type))}* me, ${Utils.generateCType(type)} value) {")
                content.appendln("${config.getIndent(1)}me->index++;")
                content.appendln("${config.getIndent(1)}if(me->index >= me->max_length)")
                content.appendln("${config.getIndent(2)}me->index = 0;")
                content.appendln()
                content.appendln("${config.getIndent(1)}me->buffer[me->index] = value;")
                content.appendln("}")
                content.appendln()

                // And finally create the get function, which will access the buffer at the correct position in the past
                // If the requested delay is larger than the max delay then an error will occur
                content.appendln("// Get function")
                content.appendln("static inline ${Utils.generateCType(type)} ${Utils.createFunctionName("Delayable", Utils.generateCType(type), "Get")}(${Utils.createTypeName("Delayable", Utils.generateCType(type))}* me, double delay) {")
                content.appendln("${config.getIndent(1)}int steps = (int) (delay / STEP_SIZE);")
                content.appendln("${config.getIndent(1)}if(steps > me->max_length)")
                content.appendln("${config.getIndent(2)}return 0; // This is an error")
                content.appendln()
                content.appendln("${config.getIndent(1)}if(steps > me->index)")
                content.appendln("${config.getIndent(2)}return me->buffer[me->max_length + me->index - steps];")
                content.appendln("${config.getIndent(1)}else")
                content.appendln("${config.getIndent(2)}return me->buffer[me->index - steps];")
                content.appendln("}")
                content.appendln()
            }

            // Close the guard
            content.appendln("#endif // DELAYABLE_H_")

            // And write the contents to disk
            File(outputDir, DELAYABLE_HEADER).writeText(content.toString())
        }

        /**
         * Generate all the files needed in the C Code generation of the given Hybrid Network. The code will be
         * generated into the provided directory, overwriting any contents that may exist
         */
        private fun generateNetwork(network: HybridNetwork, dir: String, config: Configuration = Configuration()) {
            val outputDir = File(dir)

            // If the directory doesn't already exist, we want to create it
            if(!outputDir.exists())
                outputDir.mkdirs()

            // Depending on the parametrisation method, we'll do things slightly differently
            if(config.parametrisationMethod == ParametrisationMethod.COMPILE_TIME) {
                // Compile time parametrisation means creating a C file for each instance
                for((_, instance) in network.instances) {
                    val instantiate = network.getInstantiateForInstance(instance.instance)
                    if(instantiate != null) {
                        // Create the parametrised copy of the automata
                        val definition = network.getDefinitionForInstance(instance.instance)

                        val automata = CodeGenManager.createParametrisedFsm(network, instantiate.name, instance)

                        if(definition == null || automata == null)
                            throw IllegalArgumentException("Unable to find base machine ${instantiate.name} to instantiate!")

                        // Add all the delayed types that we've found
                        delayedTypes.addAll(automata.variables.filter({it.canBeDelayed()}).map({it.type}))

                        // We need to create a sub-folder for all the instances. We can run into issues if this is the same
                        // name as the overall system, so check for that too
                        val subfolder = if(definition.name.equals(network.name, true)) { definition.name + " Files" } else { definition.name }

                        // Generate the code for the parametrised automata
                        generateItem(automata, File(outputDir, Utils.createFolderName(subfolder)).absolutePath, config)
                    }
                }
            }
            else  {
                // We only want to generate each definition once, so keep a track of them
                val generated = ArrayList<UUID>()

                for((_, instance) in network.instances) {
                    val instantiate = network.getInstantiateForInstance(instance.instance)
                    val definition = network.getDefinitionForInstance(instance.instance)
                    if(instantiate != null && definition != null && definition is HybridAutomata) {
                        // Only generate if we haven't generated this definition before
                        if (!generated.contains(instantiate.definition)) {
                            generated.add(instantiate.definition)

                            definition.name = instantiate.name

                            // Add all the delayed types that we've found
                            delayedTypes.addAll(definition.variables.filter({it.canBeDelayed()}).map({it.type}))

                            // Generate code for the unparametrised automata
                            generateItem(definition, outputDir.absolutePath, config)
                        }
                    }
                }
            }

            // Generate the Network Item
            generateItem(network, outputDir.absolutePath, config)

            // Generate Makefile
            generateMakefile(network, outputDir.absolutePath, config)
        }

        private fun makeItemsUnique(network: HybridNetwork, config: Configuration, assignedNames: ArrayList<String> = ArrayList()) {
            if(assignedNames.contains(createFileName(network.name)))
                network.name += assignedNames.size

            assignedNames.add(createFileName(network.name))

            // Depending on the parametrisation method, we'll do things slightly differently
            if(config.parametrisationMethod == ParametrisationMethod.COMPILE_TIME) {
                for((_, instance) in network.instances) {
                    val instantiate = network.getInstantiateForInstance(instance.instance)
                    if(instantiate != null) {
                        if(assignedNames.contains(createFileName(instantiate.name))) {
                            instantiate.name += assignedNames.size
                        }

                        assignedNames.add(createFileName(instantiate.name))
                    }
                }
            }
            else  {
                // Otherwise it's run time which means we only need to include once per definition type

                // So keep track of which types we've handled
                val generated = ArrayList<UUID>()
                for((_, instance) in network.instances) {
                    val instantiate = network.getInstantiateForInstance(instance.instance)
                    if(instantiate != null) {
                        // Check if we've seen this type before
                        if (!generated.contains(instantiate.definition)) {
                            // If we haven't seen it, keep track of it
                            generated.add(instantiate.definition)

                            if (assignedNames.contains(createFileName(instantiate.name)))
                                instantiate.name += assignedNames.size

                            assignedNames.add(createFileName(instantiate.name))
                        }
                    }
                }
            }
        }


        fun generate(item: HybridItem, dir: String, config: Configuration = Configuration()) {
            val outputDir = File(dir)

            // If the directory doesn't already exist, we want to create it
            if(!outputDir.exists())
                outputDir.mkdirs()

            if(item is HybridNetwork) {
                val networkDir = File(outputDir, Utils.createFolderName(item.name, "Network"))

                //For C Code we need to make sure each type is unique, so let's do that
                makeItemsUnique(item, config)

                // Generate the network
                generateNetwork(item, networkDir.absolutePath, config)

                // Generate runnable
                generateRunnable(item, outputDir.absolutePath, config)

                // Generate Makefile
                generateMakefile(item, outputDir.absolutePath, config, true)
            }
            else {
                generateItem(item, outputDir.absolutePath, config)

                // Generate runnable
                generateRunnable(item, outputDir.absolutePath, config)

                // Generate Makefile
                generateMakefile(item, outputDir.absolutePath, config, true)
            }

            // Generate Config file
            generateConfigFile(outputDir.absolutePath, config)

            // Generate Delayable files if needed
            if(delayedTypes.isNotEmpty()) {
                generateDelayableFiles(delayedTypes.distinct(), outputDir.absolutePath, config)
            }
        }
    }
}