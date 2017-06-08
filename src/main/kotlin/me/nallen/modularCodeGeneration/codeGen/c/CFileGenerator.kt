package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.finiteStateMachine.*
import me.nallen.modularCodeGeneration.parseTree.ParseTreeItem

/**
 * Created by nall426 on 8/06/2017.
 */

object CFileGenerator {
    fun generate(fsm: FiniteStateMachine): String {
        val result = StringBuilder()

        result.appendln("#include \"${fsm.name}.h\"")
        result.appendln()

        result.appendln(generateInitialisationFunction(fsm))

        result.appendln(generateExecutionFunction(fsm))

        /*result.appendln(generateStruct(fsm))

        result.appendln(generateEnum(fsm))

        result.appendln("// ${fsm.name} Initialisation function")
        result.appendln( "void ${fsm.name}Init(${fsm.name}* me);")
        result.appendln()

        result.appendln("// ${fsm.name} Execution function")
        result.appendln("void ${fsm.name}Run(${fsm.name}* me);")
        result.appendln()

        result.appendln("#endif // ${fsm.name.toUpperCase()}_H_")*/

        return result.toString().trim()
    }

    private fun generateInitialisationFunction(fsm: FiniteStateMachine): String {
        val result = StringBuilder()

        result.appendln("// ${fsm.name} Initialisation function")
        result.appendln("void ${fsm.name}Init(${fsm.name}* me) {")

        result.appendln("\t// Initialise State")
        result.appendln("\tme->state = ${fsm.init.state}")

        result.append(generateVariableInitialisationForLocality(fsm, Locality.EXTERNAL_OUTPUT))

        result.append(generateVariableInitialisationForLocality(fsm, Locality.INTERNAL))

        result.appendln("}")

        return result.toString()
    }

    private fun generateVariableInitialisationForLocality(fsm: FiniteStateMachine, locality: Locality): String {
        val result = StringBuilder()

        if(fsm.variables.count{it.locality == locality} > 0) {
            result.appendln()
            result.appendln("\t// Initialise ${locality.getTextualName()}")
            for(variable in fsm.variables
                    .filter{it.locality == locality}
                    .sortedBy { it.type }) {
                result.appendln("\t${generateVariableInitialisation(variable, fsm.init)}")
            }
        }

        return result.toString()
    }

    private fun generateVariableInitialisation(variable: Variable, init: Initialisation): String {
        var initValue: String = generateDefaultInitForType(variable.type)

        if(init.valuations.containsKey(variable.name)) {
            initValue = generateCodeForParseTreeItem(init.valuations[variable.name] !!)
        }

        return "me->${variable.name} = $initValue;"
    }

    private fun generateDefaultInitForType(type: VariableType): String {
        when(type) {
            VariableType.BOOLEAN -> return "false"
            VariableType.REAL -> return "0"
        }
    }


    private fun generateExecutionFunction(fsm: FiniteStateMachine): String {
        val result = StringBuilder()

        result.appendln("// ${fsm.name} Execution function")
        result.appendln("void ${fsm.name}Run(${fsm.name}* me) {")

        result.appendln("\t// TODO!")

        // TODO: Do this

        result.appendln("}")

        return result.toString()
    }



    private fun generateCodeForParseTreeItem(item: ParseTreeItem): String {
        //TODO: Generate string
        return "TODO"
    }
}