package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.ParameterisationMethod
import me.nallen.modularCodeGeneration.finiteStateMachine.*

object RunnableGenerator {
    private var instances: Map<String, FiniteInstance> = HashMap<String, FiniteInstance>()
    private var ioMapping: Map<MachineVariablePair, MachineVariablePair> = HashMap<MachineVariablePair, MachineVariablePair>()
    private var config: Configuration = Configuration()

    private var objects: ArrayList<CodeObject> = ArrayList<CodeObject>()

    fun generate(instances: Map<String, FiniteInstance>, ioMapping: Map<MachineVariablePair, MachineVariablePair>, config: Configuration = Configuration()): String {
        this.instances = instances
        this.ioMapping = ioMapping
        this.config = config

        objects.clear()
        for((name, instance) in instances) {
            if(config.parametrisationMethod == ParameterisationMethod.COMPILE_TIME) {
                objects.add(CodeObject(name, name))
            }
            else {
                objects.add(CodeObject(name, instance.machine))
            }
        }

        val result = StringBuilder()

        result.appendln(generateIncludes())

        result.appendln(generateVariables())

        result.appendln(generateMain())

        return result.toString().trim()
    }

    private fun generateIncludes(): String {
        val result = StringBuilder()

        result.appendln("#include <stdint.h>")
        result.appendln("#include <stdlib.h>")
        result.appendln("#include <stdio.h>")
        result.appendln("#include <string.h>")

        if(instances.isNotEmpty()) {
            result.appendln()

            if(config.parametrisationMethod == ParameterisationMethod.COMPILE_TIME) {
                for((name, instance) in instances) {
                    result.appendln("#include \"${instance.machine}/$name.h\"")
                }
            }
            else {
                val generated = ArrayList<String>()
                for((_, instance) in instances) {
                    if (!generated.contains(instance.machine)) {
                        generated.add(instance.machine)

                        result.appendln("#include \"${instance.machine}.h\"")
                    }
                }
            }
        }

        return result.toString()
    }

    private fun generateVariables(): String {
        val result = StringBuilder()

        for((name, instance) in objects) {
            result.appendln("$instance ${name}_data;")
        }

        return result.toString()
    }

    private fun generateMain(): String {
        val result = StringBuilder()

        result.appendln("int main(void) {")

        // Initialisation
        result.appendln("${config.getIndent(1)}/* Initialise Structs */")
        var first = true
        for((name, instance) in objects) {
            if(!first)
                result.appendln()
            first = false
            result.appendln("${config.getIndent(1)}(void) memset((void *)&${name}_data, 0, sizeof(${instance}));")
            result.appendln("${config.getIndent(1)}${instance}Init(&${name}_data);")

            if(config.parametrisationMethod == ParameterisationMethod.RUN_TIME) {
                for((key, value) in instances[name]!!.parameters) {
                    result.appendln("${config.getIndent(1)}${name}_data.$key = ${Utils.generateCodeForParseTreeItem(value)};")
                }
            }
        }

        result.appendln()

        // Loop
        result.appendln("${config.getIndent(1)}unsigned int i = 0;")
        result.appendln("${config.getIndent(1)}for(i=0; i < (SIMULATION_TIME / STEP_SIZE); i++) {")

        // I/O Mappings
        result.appendln("${config.getIndent(2)}/* Mappings */")
        val keys = ioMapping.keys.sortedWith(compareBy({it.machine}, {it.variable}))

        var prev = ""
        for(key in keys) {
            if(prev != "" && prev != key.machine)
                result.appendln()

            prev = key.machine
            val from = ioMapping[key]!!
            result.appendln("${config.getIndent(2)}${key.machine}_data.${key.variable} = ${from.machine}_data.${from.variable};")
        }

        result.appendln()
        result.appendln()

        // Run machines
        result.appendln("${config.getIndent(2)}/* Run Automata */")
        first = true
        for((name, instance) in objects) {
            if(!first)
                result.appendln()
            first = false
            result.appendln("${config.getIndent(2)}${instance}Run(&${name}_data);")
        }

        result.appendln("${config.getIndent(1)}}")

        result.appendln()

        result.appendln("${config.getIndent(1)}return 0;")

        result.appendln("}")

        return result.toString()
    }

    private data class CodeObject(val name: String, val type: String)
}