package me.nallen.modularCodeGeneration.codeGen.c

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import me.nallen.modularCodeGeneration.codeGen.CodeGenLanguage
import me.nallen.modularCodeGeneration.codeGen.CodeGenManager
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.ParameterisationMethod
import me.nallen.modularCodeGeneration.finiteStateMachine.FiniteInstance
import me.nallen.modularCodeGeneration.finiteStateMachine.FiniteNetwork
import me.nallen.modularCodeGeneration.finiteStateMachine.FiniteStateMachine
import me.nallen.modularCodeGeneration.finiteStateMachine.MachineVariablePair
import java.io.File

class CCodeGenerator() {
    companion object {
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

            File(outputDir, "runnable.c").writeText(RunnableGenerator.generate(instances, ioMapping, config))
        }

        fun generateNetwork(network: FiniteNetwork, dir: String, config: Configuration = Configuration()) {
            val outputDir = File(dir)

            if(!outputDir.exists())
                outputDir.mkdir()

            // Generate FSM files
            if(config.parametrisationMethod == ParameterisationMethod.COMPILE_TIME) {
                for((name, instance) in network.instances) {
                    val fsm = CodeGenManager.createParametrisedFsm(network, name, instance)

                    if(fsm == null)
                        throw IllegalArgumentException("Unable to find base machine $name to instantiate!")

                    generateFsm(fsm!!, File(outputDir, instance.machine).absolutePath, config)
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
        }
    }
}