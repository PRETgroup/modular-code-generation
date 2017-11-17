package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.ParametrisationMethod
import me.nallen.modularCodeGeneration.hybridAutomata.HybridAutomata
import me.nallen.modularCodeGeneration.hybridAutomata.Locality
import me.nallen.modularCodeGeneration.hybridAutomata.Variable

object HFileGenerator {
    private var automata: HybridAutomata = HybridAutomata("Temp")
    private var config: Configuration = Configuration()

    fun generate(automata: HybridAutomata, config: Configuration = Configuration()): String {
        this.automata = automata
        this.config = config

        val result = StringBuilder()

        result.appendln("#ifndef ${Utils.createMacroName(automata.name)}_H_")
        result.appendln("#define ${Utils.createMacroName(automata.name)}_H_")
        result.appendln()

        result.appendln(generateIncludes())

        result.appendln(generateEnum())

        result.appendln(generateStruct())

        result.appendln("// ${automata.name} Initialisation function")
        result.appendln("void ${Utils.createFunctionName(automata.name, "Init")}(${Utils.createTypeName(automata.name)}* me);")
        result.appendln()

        result.appendln("// ${automata.name} Execution function")
        result.appendln("void ${Utils.createFunctionName(automata.name, "Run")}(${Utils.createTypeName(automata.name)}* me);")
        result.appendln()

        result.appendln("#endif // ${Utils.createMacroName(automata.name)}_H_")

        return result.toString().trim()
    }

    private fun generateIncludes(): String {
        val result = StringBuilder()

        result.appendln("#include <stdint.h>")
        result.appendln("#include <stdlib.h>")
        result.appendln("#include <stdio.h>")
        result.appendln("#include <math.h>") // This may not be needed in all cases
        // TODO: Check if FSM uses a math.h function (sqrt, pow, etc.)

        result.appendln()
        result.appendln("typedef int bool;")
        result.appendln("#define false 0")
        result.appendln("#define true 1")

        result.appendln()
        if(config.parametrisationMethod == ParametrisationMethod.COMPILE_TIME)
            result.appendln("#include \"../${CCodeGenerator.CONFIG_FILE}\"")
        else
            result.appendln("#include \"${CCodeGenerator.CONFIG_FILE}\"")

        return result.toString()
    }

    private fun generateEnum(): String {
        val result = StringBuilder()

        result.appendln("// ${automata.name} States")
        result.appendln("enum ${Utils.createTypeName(automata.name, "States")} {")
        for((name) in automata.locations) {
            result.appendln("${config.getIndent(1)}${Utils.createMacroName(automata.name, name)},")
        }
        result.appendln("};")

        return result.toString()
    }

    private fun generateStruct(): String {
        val result = StringBuilder()

        result.appendln("// ${automata.name} Data Struct")
        result.appendln("typedef struct {")

        result.appendln("${config.getIndent(1)}// State")
        result.appendln("${config.getIndent(1)}enum ${Utils.createTypeName(automata.name, "States")} state;")

        result.append(Utils.performVariableFunctionForLocality(automata, Locality.EXTERNAL_INPUT, HFileGenerator::generateVariableDeclaration, config, "Declare"))
        result.append(Utils.performVariableFunctionForLocality(automata, Locality.EXTERNAL_OUTPUT, HFileGenerator::generateVariableDeclaration, config, "Declare"))
        result.append(Utils.performVariableFunctionForLocality(automata, Locality.INTERNAL, HFileGenerator::generateVariableDeclaration, config, "Declare"))
        result.append(Utils.performVariableFunctionForLocality(automata, Locality.PARAMETER, HFileGenerator::generateVariableDeclaration, config, "Declare"))

        result.appendln("} ${Utils.createTypeName(automata.name)};")

        return result.toString()
    }

    private fun generateVariableDeclaration(variable: Variable): String {
        return "${Utils.generateCType(variable.type)} ${Utils.createVariableName(variable.name)};"
    }
}