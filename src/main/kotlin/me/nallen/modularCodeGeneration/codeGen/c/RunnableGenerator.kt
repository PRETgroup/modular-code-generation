package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.CodeGenManager
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.LoggingField
import me.nallen.modularCodeGeneration.codeGen.ParametrisationMethod
import me.nallen.modularCodeGeneration.hybridAutomata.HybridNetwork

object RunnableGenerator {
    private var network: HybridNetwork = HybridNetwork()
    private var config: Configuration = Configuration()

    private var requireSelfReferenceInFunctionCalls: Boolean = false
    private var objects: ArrayList<CodeObject> = ArrayList<CodeObject>()
    private var toLog: List<LoggingField> = ArrayList<LoggingField>()

    fun generate(network: HybridNetwork, config: Configuration = Configuration()): String {
        this.network = network
        this.config = config

        this.requireSelfReferenceInFunctionCalls = config.parametrisationMethod == ParametrisationMethod.RUN_TIME

        objects.clear()
        for((name, instance) in network.instances) {
            if(config.parametrisationMethod == ParametrisationMethod.COMPILE_TIME) {
                objects.add(CodeObject(name, name))
            }
            else {
                objects.add(CodeObject(name, instance.automata))
            }
        }

        toLog = CodeGenManager.collectFieldsToLog(network, config)

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

        if(network.instances.isNotEmpty()) {
            result.appendln()

            if(config.parametrisationMethod == ParametrisationMethod.COMPILE_TIME) {
                for((name, instance) in network.instances) {
                    result.appendln("#include \"${Utils.createFolderName(instance.automata)}/${Utils.createFileName(name)}.h\"")
                }
            }
            else {
                val generated = ArrayList<String>()
                for((_, instance) in network.instances) {
                    if (!generated.contains(instance.automata)) {
                        generated.add(instance.automata)

                        result.appendln("#include \"${Utils.createFileName(instance.automata)}.h\"")
                    }
                }
            }
        }

        return result.toString()
    }

    private fun generateVariables(): String {
        val result = StringBuilder()

        for((name, instance) in objects) {
            result.appendln("${Utils.createTypeName(instance)} ${Utils.createVariableName(name, "data")};")
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
            result.appendln("${config.getIndent(1)}(void) memset((void *)&${Utils.createVariableName(name, "data")}, 0, sizeof(${Utils.createTypeName(instance)}));")
            if(config.parametrisationMethod == ParametrisationMethod.RUN_TIME)
                result.appendln("${config.getIndent(1)}${Utils.createFunctionName(instance, "Parametrise")}(&${Utils.createVariableName(name, "data")});")

            if(config.parametrisationMethod == ParametrisationMethod.RUN_TIME) {
                for((key, value) in network.instances[name]!!.parameters) {
                    result.appendln("${config.getIndent(1)}${Utils.createVariableName(name, "data")}.${Utils.createVariableName(key)} = ${Utils.generateCodeForParseTreeItem(value, Utils.PrefixData("${Utils.createVariableName(name, "data")}.", requireSelfReferenceInFunctionCalls))};")
                }
            }

            result.appendln("${config.getIndent(1)}${Utils.createFunctionName(instance, "Init")}(&${Utils.createVariableName(name, "data")});")
        }

        result.appendln()

        result.appendln("#if ENABLE_LOGGING")
        result.appendln("${config.getIndent(1)}FILE* fp = fopen(LOGGING_FILE, \"w\");")
        result.append("${config.getIndent(1)}fprintf(fp, \"Time")
        for((machine, variable, _) in toLog) {
            result.append(",$machine.$variable")
        }
        result.appendln("\\n\");")
        result.append("${config.getIndent(1)}fprintf(fp, \"%f")
        for((_, _, type) in toLog) {
            result.append(",${Utils.generatePrintfType(type)}")
        }
        result.append("\\n\", 0.0")
        for((machine, variable, _) in toLog) {
            result.append(", ${Utils.createVariableName(machine, "data")}.${Utils.createVariableName(variable)}")
        }
        result.appendln(");")
        result.appendln("${config.getIndent(1)}unsigned int last_log = 0;")
        result.appendln("#endif")
        result.appendln()

        // Loop
        result.appendln("${config.getIndent(1)}unsigned int i = 0;")
        result.appendln("${config.getIndent(1)}for(i=1; i <= (SIMULATION_TIME / STEP_SIZE); i++) {")

        // I/O Mappings
        result.appendln("${config.getIndent(2)}/* Mappings */")
        val keys = network.ioMapping.keys.sortedWith(compareBy({it.automata}, {it.variable}))

        val customVariableNames = network.instances.mapValues({Utils.createVariableName(it.key, "data")})

        var prev = ""
        for(key in keys) {
            if(prev != "" && prev != key.automata)
                result.appendln()

            prev = key.automata
            val from = network.ioMapping[key]!!
            result.appendln("${config.getIndent(2)}${Utils.createVariableName(key.automata, "data")}.${Utils.createVariableName(key.variable)} = ${Utils.generateCodeForParseTreeItem(from, Utils.PrefixData("", requireSelfReferenceInFunctionCalls, customVariableNames = customVariableNames))};")
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
            result.appendln("${config.getIndent(2)}${Utils.createFunctionName(instance, "Run")}(&${Utils.createVariableName(name, "data")});")
        }

        result.appendln()
        result.appendln()

        // Logging
        result.appendln("${config.getIndent(2)}/* Logging */")
        result.appendln("#if ENABLE_LOGGING")
        result.appendln("${config.getIndent(2)}if((i - last_log) >= LOGGING_INTERVAL / STEP_SIZE) {")
        result.append("${config.getIndent(3)}fprintf(fp, \"%f")
        for((_, _, type) in toLog) {
            result.append(",${Utils.generatePrintfType(type)}")
        }
        result.append("\\n\", i*STEP_SIZE")
        for((machine, variable, _) in toLog) {
            result.append(", ${Utils.createVariableName(machine, "data")}.${Utils.createVariableName(variable)}")
        }
        result.appendln(");")
        result.appendln("${config.getIndent(3)}last_log = i;")
        result.appendln("${config.getIndent(2)}}")
        result.appendln("#endif")

        result.appendln("${config.getIndent(1)}}")

        result.appendln()

        result.appendln("${config.getIndent(1)}return 0;")

        result.appendln("}")

        return result.toString()
    }

    private data class CodeObject(val name: String, val type: String)
}