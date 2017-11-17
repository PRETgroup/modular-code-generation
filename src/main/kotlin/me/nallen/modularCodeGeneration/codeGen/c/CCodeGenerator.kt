package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.CodeGenManager
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.ParametrisationMethod
import me.nallen.modularCodeGeneration.hybridAutomata.AutomataInstance
import me.nallen.modularCodeGeneration.hybridAutomata.HybridAutomata
import me.nallen.modularCodeGeneration.hybridAutomata.HybridNetwork
import java.io.File

class CCodeGenerator() {
    companion object {
        val RUNNABLE = "runnable.c"
        val MAKEFILE = "Makefile"
        val CONFIG_FILE = "config.h"

        private fun generateFsm(automata: HybridAutomata, dir: String, config: Configuration = Configuration()) {
            val outputDir = File(dir)

            if(!outputDir.exists())
                outputDir.mkdir()

            File(outputDir, "${Utils.createFileName(automata.name)}.h").writeText(HFileGenerator.generate(automata, config))
            File(outputDir, "${Utils.createFileName(automata.name)}.c").writeText(CFileGenerator.generate(automata, config))
        }

        private fun generateRunnable(network: HybridNetwork, dir: String, config: Configuration = Configuration()) {
            val outputDir = File(dir)

            if(!outputDir.exists())
                outputDir.mkdir()

            File(outputDir, RUNNABLE).writeText(RunnableGenerator.generate(network, config))
        }

        private fun generateMakefile(name: String, instances: Map<String, AutomataInstance>, dir: String, config: Configuration = Configuration()) {
            val outputDir = File(dir)

            if(!outputDir.exists())
                outputDir.mkdir()

            File(outputDir, MAKEFILE).writeText(MakefileGenerator.generate(name, instances, config))
        }

        private fun generateConfigFile(dir: String, config: Configuration = Configuration()) {
            val outputDir = File(dir)

            if(!outputDir.exists())
                outputDir.mkdir()

            val content = StringBuilder()

            content.appendln("#define STEP_SIZE ${config.execution.stepSize}")
            content.appendln("#define SIMULATION_TIME ${config.execution.simulationTime}")
            content.appendln("#define ENABLE_LOGGING ${if(config.logging.enable) 1 else 0}")
            content.appendln("#define LOGGING_FILE \"${config.logging.file}\"")
            content.appendln("#define LOGGING_INTERVAL ${config.logging.interval ?: config.execution.stepSize}")

            File(outputDir, CONFIG_FILE).writeText(content.toString())
        }

        fun generateNetwork(network: HybridNetwork, dir: String, config: Configuration = Configuration()) {
            val outputDir = File(dir)

            if(!outputDir.exists())
                outputDir.mkdir()

            // Generate FSM files
            if(config.parametrisationMethod == ParametrisationMethod.COMPILE_TIME) {
                for((name, instance) in network.instances) {
                    val automata = CodeGenManager.createParametrisedFsm(network, name, instance)

                    if(automata == null)
                        throw IllegalArgumentException("Unable to find base machine $name to instantiate!")

                    generateFsm(automata, File(outputDir, Utils.createFolderName(instance.automata)).absolutePath, config)
                }
            }
            else  {
                val generated = ArrayList<String>()

                for((_, instance) in network.instances) {
                    if (!generated.contains(instance.automata)) {
                        generated.add(instance.automata)

                        val automata = network.definitions.first({ it.name == instance.automata })

                        generateFsm(automata, outputDir.absolutePath, config)
                    }
                }
            }

            // Generate runnable
            generateRunnable(network, outputDir.absolutePath, config)

            // Generate Makfile
            generateMakefile(network.name, network.instances, outputDir.absolutePath, config)

            // Generate Config file
            generateConfigFile(outputDir.absolutePath, config)
        }
    }
}