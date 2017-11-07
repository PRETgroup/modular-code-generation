package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.CodeGenManager
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.ParametrisationMethod
import me.nallen.modularCodeGeneration.finiteStateMachine.FiniteInstance
import me.nallen.modularCodeGeneration.finiteStateMachine.FiniteNetwork
import me.nallen.modularCodeGeneration.finiteStateMachine.FiniteStateMachine
import me.nallen.modularCodeGeneration.finiteStateMachine.MachineVariablePair
import java.io.File

class CCodeGenerator() {
    companion object {
        val RUNNABLE = "runnable.c"
        val MAKEFILE = "Makefile"
        val CONFIG_FILE = "config.h"

        private fun generateFsm(fsm: FiniteStateMachine, dir: String, config: Configuration = Configuration()) {
            val outputDir = File(dir)

            if(!outputDir.exists())
                outputDir.mkdir()

            File(outputDir, "${fsm.name}.h").writeText(HFileGenerator.generate(fsm, config))
            File(outputDir, "${fsm.name}.c").writeText(CFileGenerator.generate(fsm, config))
        }

        private fun generateRunnable(instances: Map<String, FiniteInstance>, ioMapping: Map<MachineVariablePair, MachineVariablePair>, dir: String, config: Configuration = Configuration()) {
            val outputDir = File(dir)

            if(!outputDir.exists())
                outputDir.mkdir()

            File(outputDir, RUNNABLE).writeText(RunnableGenerator.generate(instances, ioMapping, config))
        }

        private fun generateMakefile(instances: Map<String, FiniteInstance>, dir: String, config: Configuration = Configuration()) {
            val outputDir = File(dir)

            if(!outputDir.exists())
                outputDir.mkdir()

            File(outputDir, MAKEFILE).writeText(MakefileGenerator.generate(instances, config))
        }

        private fun generateConfigFile(dir: String, config: Configuration = Configuration()) {
            val outputDir = File(dir)

            if(!outputDir.exists())
                outputDir.mkdir()

            val content = StringBuilder()

            content.appendln("#define STEP_SIZE ${config.stepSize}")
            content.appendln("#define SIMULATION_TIME ${config.simulationTime}")

            File(outputDir, CONFIG_FILE).writeText(content.toString())
        }

        fun generateNetwork(network: FiniteNetwork, dir: String, config: Configuration = Configuration()) {
            val outputDir = File(dir)

            if(!outputDir.exists())
                outputDir.mkdir()

            // Generate FSM files
            if(config.parametrisationMethod == ParametrisationMethod.COMPILE_TIME) {
                for((name, instance) in network.instances) {
                    val fsm = CodeGenManager.createParametrisedFsm(network, name, instance)

                    if(fsm == null)
                        throw IllegalArgumentException("Unable to find base machine $name to instantiate!")

                    generateFsm(fsm, File(outputDir, instance.machine).absolutePath, config)
                }
            }
            else  {
                val generated = ArrayList<String>()

                for((_, instance) in network.instances) {
                    if (!generated.contains(instance.machine)) {
                        generated.add(instance.machine)

                        val fsm = network.definitions.first({ it.name == instance.machine })

                        generateFsm(fsm, outputDir.absolutePath, config)
                    }
                }
            }

            // Generate runnable
            generateRunnable(network.instances, network.ioMapping, outputDir.absolutePath, config)

            // Generate Makfile
            generateMakefile(network.instances, outputDir.absolutePath, config)

            // Generate Config file
            generateConfigFile(outputDir.absolutePath, config)
        }
    }
}