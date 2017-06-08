package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.finiteStateMachine.FiniteStateMachine
import me.nallen.modularCodeGeneration.finiteStateMachine.Locality
import me.nallen.modularCodeGeneration.finiteStateMachine.Variable
import me.nallen.modularCodeGeneration.finiteStateMachine.VariableType

/**
 * Created by nall426 on 8/06/2017.
 */

object HFileGenerator {
    fun generate(fsm: FiniteStateMachine): String {
        val result = StringBuilder()

        result.appendln("#ifndef ${fsm.name.toUpperCase()}_H_")
        result.appendln("#define ${fsm.name.toUpperCase()}_H_")
        result.appendln()

        result.appendln(generateIncludes(fsm))

        result.appendln(generateStruct(fsm))

        result.appendln(generateEnum(fsm))

        result.appendln("// ${fsm.name} Initialisation function")
        result.appendln("void ${fsm.name}Init(${fsm.name}* me);")
        result.appendln()

        result.appendln("// ${fsm.name} Execution function")
        result.appendln("void ${fsm.name}Run(${fsm.name}* me);")
        result.appendln()

        result.appendln("#endif // ${fsm.name.toUpperCase()}_H_")

        return result.toString().trim()
    }

    private fun generateIncludes(fsm: FiniteStateMachine): String {
        val result = StringBuilder()

        result.appendln("#include <stdint.h>")
        result.appendln("#include <stdlib.h>")
        result.appendln("#include <stdio.h>")
        result.appendln("#include <math.h>") // This may not be needed in all cases
        // TODO: Check if FSM uses a math.h function (sqrt, pow, etc.)

        return result.toString()
    }

    private fun generateEnum(fsm: FiniteStateMachine): String {
        val result = StringBuilder()

        result.appendln("// ${fsm.name} States")
        result.appendln("enum ${fsm.name}States {")
        for((name) in fsm.states) {
            result.appendln("\t$name,")
        }
        result.appendln("};")

        return result.toString()
    }

    private fun generateStruct(fsm: FiniteStateMachine): String {
        val result = StringBuilder()

        result.appendln("// ${fsm.name} Data Struct")
        result.appendln("typedef stuct {")

        result.appendln("\t// State")
        result.appendln("\tenum ${fsm.name}States state;")

        result.append(generateVariableDeclarationsForLocality(fsm, Locality.EXTERNAL_INPUT))

        result.append(generateVariableDeclarationsForLocality(fsm, Locality.EXTERNAL_OUTPUT))

        result.append(generateVariableDeclarationsForLocality(fsm, Locality.INTERNAL))

        result.appendln("} ${fsm.name};")

        return result.toString()
    }

    private fun generateVariableDeclarationsForLocality(fsm: FiniteStateMachine, locality: Locality): String {
        val result = StringBuilder()

        if(fsm.variables.count{it.locality == locality} > 0) {
            result.appendln()
            result.appendln("\t// ${locality.getTextualName()}")
            for(variable in fsm.variables
                    .filter{it.locality == locality}
                    .sortedBy { it.type }) {
                result.appendln("\t${generateVariableDeclaration(variable)}")
            }
        }

        return result.toString()
    }

    private fun generateVariableDeclaration(variable: Variable): String {
        return "${generateCType(variable.type)} ${variable.name};"
    }

    private fun generateCType(type: VariableType): String {
        when(type) {
            VariableType.BOOLEAN -> return "bool"
            VariableType.REAL -> return "double"
        }
    }
}