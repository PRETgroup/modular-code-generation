package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.finiteStateMachine.*
import me.nallen.modularCodeGeneration.finiteStateMachine.Variable

object CFileGenerator {
    private var fsm: FiniteStateMachine = FiniteStateMachine("Temp")
    private var config: Configuration = Configuration()

    fun generate(fsm: FiniteStateMachine, config: Configuration = Configuration()): String {
        this.fsm = fsm
        this.config = config

        val result = StringBuilder()

        result.appendln("#include \"${fsm.name}.h\"")
        result.appendln()

        result.appendln(generateInitialisationFunction())

        result.appendln(generateExecutionFunction())

        return result.toString().trim()
    }

    private fun generateInitialisationFunction(): String {
        val result = StringBuilder()

        result.appendln("// ${fsm.name} Initialisation function")
        result.appendln("void ${fsm.name}Init(${fsm.name}* me) {")

        result.appendln("${config.getIndent(1)}// Initialise State")
        result.appendln("${config.getIndent(1)}me->state = ${fsm.init.state}")

        result.append(Utils.performVariableFunctionForLocality(fsm, Locality.EXTERNAL_OUTPUT, CFileGenerator::generateVariableInitialisation, config, "Initialise"))

        result.append(Utils.performVariableFunctionForLocality(fsm, Locality.INTERNAL, CFileGenerator::generateVariableInitialisation, config, "Initialise"))

        result.appendln("}")

        return result.toString()
    }

    private fun generateVariableInitialisation(variable: Variable): String {
        var initValue: String = generateDefaultInitForType(variable.type)

        if(fsm.init.valuations.containsKey(variable.name)) {
            initValue = Utils.generateCodeForParseTreeItem(fsm.init.valuations[variable.name] !!)
        }

        return "me->${variable.name} = $initValue;"
    }

    private fun generateDefaultInitForType(type: VariableType): String {
        when(type) {
            VariableType.BOOLEAN -> return "false"
            VariableType.REAL -> return "0"
        }
    }


    private fun generateExecutionFunction(): String {
        val result = StringBuilder()

        result.appendln("// ${fsm.name} Execution function")
        result.appendln("void ${fsm.name}Run(${fsm.name}* me) {")

        result.appendln("${config.getIndent(1)}// Create intermediary variables")
        result.appendln("${config.getIndent(1)}enum ${fsm.name}States state_u = me->state;")

        result.append(Utils.performVariableFunctionForLocality(fsm, Locality.EXTERNAL_OUTPUT, CFileGenerator::generateIntermediateVariable, config))

        result.append(Utils.performVariableFunctionForLocality(fsm, Locality.INTERNAL, CFileGenerator::generateIntermediateVariable, config))
        result.appendln()

        result.appendln(generateStateMachine())

        result.appendln("${config.getIndent(1)}// Update from intermediary variables")
        result.appendln("${config.getIndent(1)}me->state = state_u;")

        result.append(Utils.performVariableFunctionForLocality(fsm, Locality.EXTERNAL_OUTPUT, CFileGenerator::updateFromIntermediateVariable, config))

        result.append(Utils.performVariableFunctionForLocality(fsm, Locality.INTERNAL, CFileGenerator::updateFromIntermediateVariable, config))

        result.appendln("}")

        return result.toString()
    }

    private fun generateStateMachine(): String {
        val result = StringBuilder()

        result.appendln("${config.getIndent(1)}switch(me->state) {")

        for((name) in fsm.states) {
            result.appendln("${config.getIndent(2)}case $name: // Logic for state $name")

            var atLeastOneIf = false
            for((_, toLocation, guard, update) in fsm.transitions.filter{it.fromLocation == name }) {
                result.appendln("${config.getIndent(3)}${if(atLeastOneIf) { "else " } else { "" }}if(${Utils.generateCodeForParseTreeItem(guard)}) {")

                for((variable, equation) in update) {
                    result.appendln("${config.getIndent(4)}${variable}_u = ${Utils.generateCodeForParseTreeItem(equation)};")
                }

                if(update.isNotEmpty())
                    result.appendln()

                result.appendln("${config.getIndent(4)}// Next state is $toLocation")
                result.appendln("${config.getIndent(4)}state_u = $toLocation;")

                result.appendln("${config.getIndent(3)}}")

                atLeastOneIf = true
            }

            result.appendln("${config.getIndent(3)}break;")
        }

        result.appendln("${config.getIndent(1)}}")

        return result.toString()
    }

    private fun generateIntermediateVariable(variable: Variable): String {
        return "${Utils.generateCType(variable.type)} ${variable.name}_u = me->${variable.name};"
    }

    private fun updateFromIntermediateVariable(variable: Variable): String {
        return "me->${variable.name} = ${variable.name}_u;"
    }
}