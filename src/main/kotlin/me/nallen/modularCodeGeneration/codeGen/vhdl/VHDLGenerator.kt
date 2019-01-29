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
        const val LIBRARY_FILE = "lib.vhdl"

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
            content.appendln("use work.lib.all;")
            content.appendln("")
            content.appendln("package config is")
            content.appendln("")
            content.appendln("    constant step_size : signed(31 downto 0) := CREATE_FP(${config.execution.stepSize});")
            content.appendln("")
            content.appendln("end package config;")

            // And write the content
            File(outputDir, CONFIG_FILE).writeText(content.toString())

            // Generate the Fixed Point library
            File(outputDir, LIBRARY_FILE).writeText(this::class.java.classLoader.getResource("templates/vhdl/lib.vhdl").readText())
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

            // We only want to generate each definition once (because generics), so keep track of them
            val generated = ArrayList<UUID>()

            for((_, instance) in network.instances) {
                // Get the instance of the item we want to generate
                val instantiate = network.getInstantiateForInstantiateId(instance.instantiate)
                val definition = network.getDefinitionForInstantiateId(instance.instantiate)
                if(instantiate != null && definition != null) {
                    // Only generate if we haven't generated this definition before
                    if (!generated.contains(instantiate.definition)) {
                        generated.add(instantiate.definition)

                        // Generate code for the item
                        generateItem(definition, outputDir.absolutePath, config)
                    }
                }
            }
        }

        /**
         * Generate code files for the given Hybrid Item (either a Network or Automata). The code will be placed into
         * the provided directory, overwriting any contents that may already exist.
         */
        fun generate(item: HybridItem, dir: String, config: Configuration = Configuration()) {
            val outputDir = File(dir)

            var generateItem = item

            // Check if we're meant to be doing run-time parametrisation but we don't have a flat network
            if(config.runTimeParametrisation && generateItem is HybridNetwork) {
                // We need to flatten the network so we can generate efficient code
                // Let's warn the user first
                println("[INFO] VHDL Run-time generation requires a \"flat\" network. Network has automatically been " +
                        "flattened where required.")

                // And then flatten the network
                generateItem = generateItem.flatten()
            }

            // If the directory doesn't already exist, we want to create it
            if(!outputDir.exists())
                outputDir.mkdirs()

            // If we're generating code for a Hybrid Network, we need to create a sub-directory for the files
            val itemDir = if(generateItem is HybridNetwork) {
                File(outputDir, Utils.createFolderName(generateItem.name, "Network"))
            }
            else {
                outputDir
            }

            // Generate the current item
            generateItem(generateItem, itemDir.absolutePath, config)

            // Generate runnable
            generateSystem(generateItem, outputDir.absolutePath, config)

            // Generate Config file
            generateConfigFile(outputDir.absolutePath, config)
        }
    }
}