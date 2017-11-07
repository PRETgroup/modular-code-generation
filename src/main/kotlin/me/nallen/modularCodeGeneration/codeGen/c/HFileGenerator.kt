package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.ParametrisationMethod
import me.nallen.modularCodeGeneration.finiteStateMachine.FiniteStateMachine
import me.nallen.modularCodeGeneration.finiteStateMachine.Locality
import me.nallen.modularCodeGeneration.finiteStateMachine.Variable

object HFileGenerator {
    private var fsm: FiniteStateMachine = FiniteStateMachine("Temp")
    private var config: Configuration = Configuration()

    fun generate(fsm: FiniteStateMachine, config: Configuration = Configuration()): String {
        this.fsm = fsm
        this.config = config

        val result = StringBuilder()

        result.appendln("#ifndef ${Utils.createMacroName(fsm.name)}_H_")
        result.appendln("#define ${Utils.createMacroName(fsm.name)}_H_")
        result.appendln()

        result.appendln(generateIncludes())

        result.appendln(generateEnum())

        result.appendln(generateStruct())

        result.appendln("// ${fsm.name} Initialisation function")
        result.appendln("void ${Utils.createFunctionName(fsm.name, "Init")}(${Utils.createTypeName(fsm.name)}* me);")
        result.appendln()

        result.appendln("// ${fsm.name} Execution function")
        result.appendln("void ${Utils.createFunctionName(fsm.name, "Run")}(${Utils.createTypeName(fsm.name)}* me);")
        result.appendln()

        result.appendln("#endif // ${Utils.createMacroName(fsm.name)}_H_")

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
        if(config.parametrisationMethod == ParametrisationMethod.COMPILE_TIME)
            result.appendln("#include \"../${CCodeGenerator.CONFIG_FILE}\"")
        else
            result.appendln("#include \"${CCodeGenerator.CONFIG_FILE}\"")

        return result.toString()
    }

    private fun generateEnum(): String {
        val result = StringBuilder()

        result.appendln("// ${fsm.name} States")
        result.appendln("enum ${Utils.createTypeName(fsm.name, "States")} {")
        for((name) in fsm.states) {
            result.appendln("${config.getIndent(1)}${Utils.createMacroName(fsm.name, name)},")
        }
        result.appendln("};")

        return result.toString()
    }

    private fun generateStruct(): String {
        val result = StringBuilder()

        result.appendln("// ${fsm.name} Data Struct")
        result.appendln("typedef struct {")

        result.appendln("${config.getIndent(1)}// State")
        result.appendln("${config.getIndent(1)}enum ${Utils.createTypeName(fsm.name, "States")} state;")

        result.append(Utils.performVariableFunctionForLocality(fsm, Locality.EXTERNAL_INPUT, HFileGenerator::generateVariableDeclaration, config, "Declare"))
        result.append(Utils.performVariableFunctionForLocality(fsm, Locality.EXTERNAL_OUTPUT, HFileGenerator::generateVariableDeclaration, config, "Declare"))
        result.append(Utils.performVariableFunctionForLocality(fsm, Locality.INTERNAL, HFileGenerator::generateVariableDeclaration, config, "Declare"))
        result.append(Utils.performVariableFunctionForLocality(fsm, Locality.PARAMETER, HFileGenerator::generateVariableDeclaration, config, "Declare"))

        result.appendln("} ${Utils.createTypeName(fsm.name)};")

        return result.toString()
    }

    private fun generateVariableDeclaration(variable: Variable): String {
        return "${Utils.generateCType(variable.type)} ${Utils.createVariableName(variable.name)};"
    }
}