package me.nallen.modularCodeGeneration.codeGen.vhdl

import me.nallen.modularCodeGeneration.codeGen.CodeGenManager
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.ParametrisationMethod
import me.nallen.modularCodeGeneration.codeGen.vhdl.Utils.createFileName
import me.nallen.modularCodeGeneration.hybridAutomata.HybridAutomata
import me.nallen.modularCodeGeneration.hybridAutomata.HybridItem
import me.nallen.modularCodeGeneration.hybridAutomata.HybridNetwork
import me.nallen.modularCodeGeneration.parseTree.VariableType
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

/**
 * The class that contains methods to do with the generation of C code for a Hybrid Item.
 *
 * All generated files and the generated code itself will follow proper C style conventions.
 */
class VHDLGenerator {
    companion object {
        const val SYSTEM = "system.vhdl"
        const val CONFIG_FILE = "config.vhdl"

        // We need to keep track of the variables we need to delay
        private val delayedTypes = ArrayList<VariableType>()

        /**
         * Generate C code that captures the Hybrid Item. The code will be placed into the provided directory,
         * overwriting any contents that may already exist.
         */
        private fun generateItem(item: HybridItem, dir: String, config: Configuration) {
            val outputDir = File(dir)

            // If the directory doesn't already exist, we want to create it
            if(!outputDir.exists())
                outputDir.mkdirs()

            // If it's a Network, then we also need to generate the sub-files
            if(item is HybridNetwork) {
                generateNetworkItems(item, outputDir.absolutePath, config)
            }

            if(item is HybridAutomata) {
                // Generate the Source File for the Automata
                File(outputDir, "${Utils.createFileName(item.name)}.vhdl").writeText(AutomataGenerator.generate(item, config))
            }
            else if(item is HybridNetwork) {
                // Generate the Source File for the Network
                File(outputDir, "${Utils.createFileName(item.name)}.vhdl").writeText(NetworkGenerator.generate(item, config))
            }
        }

        /**
         * Generate C code for the overall runnable that represents a Hybrid Item. The code will be placed into the
         * provided directory, overwriting any contents that may already exist.
         */
        private fun generateSystem(item: HybridItem, dir: String, config: Configuration = Configuration()) {
            val outputDir = File(dir)

            // If the directory doesn't already exist, we want to create it
            if(!outputDir.exists())
                outputDir.mkdirs()

            // Generate the Runnable File
            File(outputDir, SYSTEM).writeText(SystemGenerator.generate(item, config))
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
            content.appendln("library ieee;")
            content.appendln("use ieee.std_logic_1164.all;")
            content.appendln("use ieee.numeric_std.all;")
            content.appendln("")
            content.appendln("package config is")
            content.appendln("")
            content.appendln("    constant step_size : signed(31 downto 0) := to_signed(${Utils.convertToFixedPoint(config.execution.stepSize, 16)}, 32); -- ${config.execution.stepSize}")
            content.appendln("")
            content.appendln("end package config;")

            // And write the content
            File(outputDir, CONFIG_FILE).writeText(content.toString())
        }

        /**
         * Generate all the files needed in the C Code generation of the given Hybrid Network. The code will be
         * generated into the provided directory, overwriting any contents that may exist
         */
        private fun generateNetworkItems(network: HybridNetwork, dir: String, config: Configuration = Configuration()) {
            val outputDir = File(dir)

            // If the directory doesn't already exist, we want to create it
            if(!outputDir.exists())
                outputDir.mkdirs()

            //For C Code we need to make sure each type is unique, so let's do that
            makeItemsUnique(network, config)

            // Depending on the parametrisation method, we'll do things slightly differently
            if(config.parametrisationMethod == ParametrisationMethod.COMPILE_TIME) {
                // Compile time parametrisation means creating a VHDL file for each instantiate
                for((_, instance) in network.instances) {
                    // Get the instance of the item we want to generate
                    val instantiate = network.getInstantiateForInstantiateId(instance.instantiate)
                    if(instantiate != null) {
                        // Create the parametrised copy of the automata
                        val definition = network.getDefinitionForDefinitionId(instantiate.definition)

                        val item = CodeGenManager.createParametrisedItem(network, instantiate.name, instance)

                        if(definition == null || item == null)
                            throw IllegalArgumentException("Unable to find base machine ${instantiate.name} to instantiate!")

                        // Add all the delayed types that we've found
                        delayedTypes.addAll(item.variables.filter({it.canBeDelayed()}).map({it.type}))

                        // We need to create a sub-folder for all the instances. We can run into issues if this is the same
                        // name as the overall system, so check for that too
                        val subfolder = if(definition.name.equals(network.name, true)) { definition.name + " Files" } else { definition.name }

                        // Generate the code for the parametrised item
                        generateItem(item, File(outputDir, Utils.createFolderName(subfolder)).absolutePath, config)
                    }
                }
            }
            else  {
                // We only want to generate each definition once, so keep a track of them
                val generated = ArrayList<UUID>()

                for((_, instance) in network.instances) {
                    // Get the instance of the item we want to generate
                    val instantiate = network.getInstantiateForInstantiateId(instance.instantiate)
                    val definition = network.getDefinitionForInstantiateId(instance.instantiate)
                    if(instantiate != null && definition != null) {
                        // Only generate if we haven't generated this definition before
                        if (!generated.contains(instantiate.definition)) {
                            generated.add(instantiate.definition)

                            definition.name = instantiate.name

                            // Add all the delayed types that we've found
                            delayedTypes.addAll(definition.variables.filter({it.canBeDelayed()}).map({it.type}))

                            // Generate code for the unparametrised item
                            generateItem(definition, outputDir.absolutePath, config)
                        }
                    }
                }
            }
        }

        /**
         * Makes the set of instances within the given Network unique for the desired parametrisation method.
         */
        private fun makeItemsUnique(network: HybridNetwork, config: Configuration, assignedNames: ArrayList<String> = ArrayList()): ArrayList<String> {
            if(assignedNames.contains(createFileName(network.name)))
                network.name += assignedNames.size

            assignedNames.add(createFileName(network.name))

            // Depending on the parametrisation method, we'll do things slightly differently
            if(config.parametrisationMethod == ParametrisationMethod.COMPILE_TIME) {
                // Iterate over every instance
                for((_, instance) in network.instances) {
                    // Get the item we're currently checking
                    val instantiate = network.getInstantiateForInstantiateId(instance.instantiate)
                    if(instantiate != null) {
                        // Check if we've seen this item before
                        if(assignedNames.contains(createFileName(instantiate.name)))
                            // If we have, we add a number to the end
                            instantiate.name += assignedNames.size

                        // Keep track of what we've seen
                        assignedNames.add(createFileName(instantiate.name))
                    }
                }
            }
            else  {
                // Otherwise it's run time which means we only need to include once per definition type

                // So keep track of which types we've handled
                val generated = ArrayList<UUID>()
                // Iterate over every instance
                for((_, instance) in network.instances) {
                    // Get the item we're currently checking
                    val instantiate = network.getInstantiateForInstantiateId(instance.instantiate)
                    if(instantiate != null) {
                        // Check if we've seen this type before
                        if (!generated.contains(instantiate.definition)) {
                            // If we haven't seen it, keep track of it
                            generated.add(instantiate.definition)

                            // Check if we've seen this item before
                            if (assignedNames.contains(createFileName(instantiate.name)))
                            // If we have, we add a number to the end
                                instantiate.name += assignedNames.size

                            // Keep track of what we've seen
                            assignedNames.add(createFileName(instantiate.name))
                        }
                    }
                }
            }

            // Return the list of names we've seen so far
            return assignedNames
        }

        /**
         * Generate code files for the given Hybrid Item (either a Network or Automata). The code will be placed into
         * the provided directory, overwriting any contents that may already exist.
         */
        fun generate(item: HybridItem, dir: String, config: Configuration = Configuration()) {
            val outputDir = File(dir)

            // If the directory doesn't already exist, we want to create it
            if(!outputDir.exists())
                outputDir.mkdirs()

            // If we're generating code for a Hybrid Network, we need to create a sub-directory for the files
            val itemDir = if(item is HybridNetwork) {
                File(outputDir, Utils.createFolderName(item.name, "Network"))
            }
            else {
                outputDir
            }

            // Generate the current item
            generateItem(item, itemDir.absolutePath, config)

            // Generate runnable
            generateSystem(item, outputDir.absolutePath, config)

            // Generate Config file
            generateConfigFile(outputDir.absolutePath, config)

            // Generate Delayable files if needed
            if(delayedTypes.isNotEmpty()) {
                throw NotImplementedError("Delayed Types are currently not supported in VHDL Generation")
            }
        }
    }
}