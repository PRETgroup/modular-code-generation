package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.CodeGenManager
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.ParametrisationMethod
import me.nallen.modularCodeGeneration.hybridAutomata.AutomataInstance
import me.nallen.modularCodeGeneration.hybridAutomata.HybridAutomata
import me.nallen.modularCodeGeneration.hybridAutomata.HybridNetwork
import me.nallen.modularCodeGeneration.parseTree.VariableType
import java.io.File

class CCodeGenerator() {
    companion object {
        val RUNNABLE = "runnable.c"
        val MAKEFILE = "Makefile"
        val CONFIG_FILE = "config.h"
        val DELAYABLE_HEADER = "delayable.h"
        val DELAYABLE_SOURCE = "delayable.c"

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

        private fun generateMakefile(name: String, instances: Map<String, AutomataInstance>, needsDelayed: Boolean, dir: String, config: Configuration = Configuration()) {
            val outputDir = File(dir)

            if(!outputDir.exists())
                outputDir.mkdir()

            File(outputDir, MAKEFILE).writeText(MakefileGenerator.generate(name, instances, needsDelayed, config))
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

        private fun generateDelayableFiles(types: List<VariableType>, dir: String, config: Configuration = Configuration()) {
            val outputDir = File(dir)

            if(!outputDir.exists())
                outputDir.mkdir()

            val content = StringBuilder()

            content.appendln("#ifndef DELAYABLE_H_")
            content.appendln("#define DELAYABLE_H_")
            content.appendln()

            content.appendln("#include <stdlib.h>")
            content.appendln()

            if(types.contains(VariableType.BOOLEAN)) {
                content.appendln("typedef int bool;")
                content.appendln("#define false 0")
                content.appendln("#define true 1")
                content.appendln()
            }

            content.appendln("#include \"$CONFIG_FILE\"")
            content.appendln()

            for(type in types) {
                content.appendln("// Delayable struct for type ${Utils.generateCType(type)}")
                content.appendln("typedef struct {")
                content.appendln("${config.getIndent(1)}${Utils.generateCType(type)} *buffer;")
                content.appendln("${config.getIndent(1)}unsigned int index;")
                content.appendln("${config.getIndent(1)}unsigned int max_length;")
                content.appendln("} ${Utils.createTypeName("Delayable", Utils.generateCType(type))};")
                content.appendln()

                content.appendln("// Initialisation function")
                content.appendln("void ${Utils.createFunctionName("Delayable", Utils.generateCType(type), "Init")}(${Utils.createTypeName("Delayable", Utils.generateCType(type))}* me, double max_delay);")
                content.appendln()

                content.appendln("// Add function")
                content.appendln("void ${Utils.createFunctionName("Delayable", Utils.generateCType(type), "Add")}(${Utils.createTypeName("Delayable", Utils.generateCType(type))}* me, ${Utils.generateCType(type)} value);")
                content.appendln()

                content.appendln("// Get function")
                content.appendln("${Utils.generateCType(type)} ${Utils.createFunctionName("Delayable", Utils.generateCType(type), "Get")}(${Utils.createTypeName("Delayable", Utils.generateCType(type))}* me, double delay);")
                content.appendln()
            }

            content.appendln("#endif // DELAYABLE_H_")

            File(outputDir, DELAYABLE_HEADER).writeText(content.toString())

            content.setLength(0)

            content.appendln("#include \"$DELAYABLE_HEADER\"")
            content.appendln()

            for(type in types) {
                content.appendln("// Initialisation function")
                content.appendln("void ${Utils.createFunctionName("Delayable", Utils.generateCType(type), "Init")}(${Utils.createTypeName("Delayable", Utils.generateCType(type))}* me, double max_delay) {")
                content.appendln("${config.getIndent(1)}me->index = 0;")
                content.appendln("${config.getIndent(1)}me->max_length = (unsigned int) (max_delay / STEP_SIZE);")
                content.appendln("${config.getIndent(1)}me->buffer = malloc(sizeof(${Utils.generateCType(type)}) * me->max_length);")
                content.appendln("}")
                content.appendln()

                content.appendln("// Add function")
                content.appendln("void ${Utils.createFunctionName("Delayable", Utils.generateCType(type), "Add")}(${Utils.createTypeName("Delayable", Utils.generateCType(type))}* me, ${Utils.generateCType(type)} value) {")
                content.appendln("${config.getIndent(1)}me->index++;")
                content.appendln("${config.getIndent(1)}if(me->index >= me->max_length)")
                content.appendln("${config.getIndent(2)}me->index = 0;")
                content.appendln()
                content.appendln("${config.getIndent(1)}me->buffer[me->index] = value;")
                content.appendln("}")
                content.appendln()

                content.appendln("// Get function")
                content.appendln("${Utils.generateCType(type)} ${Utils.createFunctionName("Delayable", Utils.generateCType(type), "Get")}(${Utils.createTypeName("Delayable", Utils.generateCType(type))}* me, double delay) {")
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

            File(outputDir, DELAYABLE_SOURCE).writeText(content.toString())
        }

        fun generateNetwork(network: HybridNetwork, dir: String, config: Configuration = Configuration()) {
            val outputDir = File(dir)

            if(!outputDir.exists())
                outputDir.mkdir()

            val delayedTypes = ArrayList<VariableType>()
            // Generate FSM files
            if(config.parametrisationMethod == ParametrisationMethod.COMPILE_TIME) {
                for((name, instance) in network.instances) {
                    val automata = CodeGenManager.createParametrisedFsm(network, name, instance)

                    if(automata == null)
                        throw IllegalArgumentException("Unable to find base machine $name to instantiate!")

                    delayedTypes.addAll(automata.variables.filter({it.delayableBy > 0}).map({it.type}))

                    generateFsm(automata, File(outputDir, Utils.createFolderName(instance.automata)).absolutePath, config)
                }
            }
            else  {
                val generated = ArrayList<String>()

                for((_, instance) in network.instances) {
                    if (!generated.contains(instance.automata)) {
                        generated.add(instance.automata)

                        val automata = network.definitions.first({ it.name == instance.automata })

                        delayedTypes.addAll(automata.variables.filter({it.delayableBy > 0}).map({it.type}))

                        generateFsm(automata, outputDir.absolutePath, config)
                    }
                }
            }

            // Generate runnable
            generateRunnable(network, outputDir.absolutePath, config)

            // Generate Makfile
            generateMakefile(network.name, network.instances, delayedTypes.isNotEmpty(), outputDir.absolutePath, config)

            // Generate Config file
            generateConfigFile(outputDir.absolutePath, config)

            // Generate Delayable files if needed
            if(delayedTypes.isNotEmpty()) {
                generateDelayableFiles(delayedTypes.distinct(), outputDir.absolutePath, config)
            }
        }
    }
}