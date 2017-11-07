package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.ParametrisationMethod
import me.nallen.modularCodeGeneration.finiteStateMachine.*
import me.nallen.modularCodeGeneration.finiteStateMachine.Variable
import me.nallen.modularCodeGeneration.parseTree.Literal

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
        result.appendln("${config.getIndent(1)}me->state = ${fsm.name}_${fsm.init.state};")

        result.append(Utils.performVariableFunctionForLocality(fsm, Locality.EXTERNAL_OUTPUT, CFileGenerator::generateVariableInitialisation, config, "Initialise"))

        result.append(Utils.performVariableFunctionForLocality(fsm, Locality.INTERNAL, CFileGenerator::generateVariableInitialisation, config, "Initialise"))

        if(config.parametrisationMethod == ParametrisationMethod.RUN_TIME) {
            if(fsm.variables.any({it.locality == Locality.PARAMETER && it.defaultValue != null}))
                result.append(Utils.performVariableFunctionForLocality(fsm, Locality.PARAMETER, CFileGenerator::generateParameterInitialisation, config, "Initialise Default"))
        }

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
            VariableType.BOOLEAN -> return Utils.generateCodeForParseTreeItem(Literal("false"))
            VariableType.REAL -> return Utils.generateCodeForParseTreeItem(Literal("0"))
        }
    }

    private fun generateParameterInitialisation(variable: Variable): String {
        if(variable.defaultValue != null) {
            return "me->${variable.name} = ${Utils.generateCodeForParseTreeItem(variable.defaultValue!!)};"
        }

        return ""
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


        val needsTransitionCounting = config.maximumInterTransitions > 1
        val defaultIndent = if(needsTransitionCounting) 2 else 1

        if(needsTransitionCounting) {
            result.appendln("${config.getIndent(1)}unsigned int remainingTransitions = ${config.maximumInterTransitions};")
            result.appendln("${config.getIndent(1)}while(remainingTransitions > 0) {")
            result.appendln("${config.getIndent(2)}// Decrement the remaining transitions available")
            result.appendln("${config.getIndent(2)}remainingTransitions--;")
            result.appendln()
        }

        result.appendln(generateStateMachine(needsTransitionCounting))

        result.appendln("${config.getIndent(defaultIndent)}// Update from intermediary variables")
        result.appendln("${config.getIndent(defaultIndent)}me->state = state_u;")

        result.append(Utils.performVariableFunctionForLocality(fsm, Locality.EXTERNAL_OUTPUT, CFileGenerator::updateFromIntermediateVariable, config, depth=defaultIndent))

        result.append(Utils.performVariableFunctionForLocality(fsm, Locality.INTERNAL, CFileGenerator::updateFromIntermediateVariable, config, depth=defaultIndent))

        if(needsTransitionCounting) {
            result.appendln("${config.getIndent(1)}}")
        }

        if(config.requireOneIntraTransitionPerTick) {
            result.appendln()

            result.append(generateIntraStateMachine())
        }

        result.appendln("}")

        return result.toString()
    }

    private fun generateStateMachine(countTransitions: Boolean): String {
        val result = StringBuilder()

        val defaultIndent = if(countTransitions) 2 else 1

        result.appendln("${config.getIndent(defaultIndent)}// Run the state machine for transition logic")
        if(config.requireOneIntraTransitionPerTick)
            result.appendln("${config.getIndent(defaultIndent)}// This will only be inter-location transitions, with intra-location transitions happening later")

        result.appendln("${config.getIndent(defaultIndent)}switch(me->state) {")

        for((name) in fsm.states) {
            result.appendln("${config.getIndent(defaultIndent+1)}case ${fsm.name}_$name: // Logic for state $name")

            var atLeastOneIf = false
            for((fromLocation, toLocation, guard, update) in fsm.transitions.filter{it.fromLocation == name && (!config.requireOneIntraTransitionPerTick || it.fromLocation != it.toLocation) }) {
                result.appendln("${config.getIndent(defaultIndent+2)}${if(atLeastOneIf) { "else " } else { "" }}if(${Utils.generateCodeForParseTreeItem(guard)}) {")

                for((variable, equation) in update) {
                    result.appendln("${config.getIndent(defaultIndent+3)}${variable}_u = ${Utils.generateCodeForParseTreeItem(equation)};")
                }

                if(update.isNotEmpty())
                    result.appendln()

                result.appendln("${config.getIndent(defaultIndent+3)}// Next state is $toLocation")
                result.appendln("${config.getIndent(defaultIndent+3)}state_u = ${fsm.name}_$toLocation;")

                if(countTransitions && toLocation == fromLocation) {
                    result.appendln()
                    result.appendln("${config.getIndent(defaultIndent+3)}// Taking an intra-location transition stops execution")
                    result.appendln("${config.getIndent(defaultIndent+3)}remainingTransitions = 0;")
                }

                result.appendln("${config.getIndent(defaultIndent+2)}}")

                atLeastOneIf = true
            }

            if(atLeastOneIf && countTransitions) {
                result.appendln("${config.getIndent(defaultIndent+2)}else {")
                result.appendln("${config.getIndent(defaultIndent+3)}// No available transition stops execution")
                result.appendln("${config.getIndent(defaultIndent+3)}remainingTransitions = 0;")
                result.appendln("${config.getIndent(defaultIndent+2)}}")
            }

            result.appendln("${config.getIndent(defaultIndent+2)}break;")
        }

        result.appendln("${config.getIndent(defaultIndent)}}")

        return result.toString()
    }

    private fun generateIntraStateMachine(): String {
        val result = StringBuilder()

        result.appendln("${config.getIndent(1)}// Intra-location logic for every state")
        result.appendln("${config.getIndent(1)}switch(me->state) {")

        for((name) in fsm.states) {
            result.appendln("${config.getIndent(2)}case $name: // Intra-location logic for state $name")

            for((_, _, _, update) in fsm.transitions.filter{it.fromLocation == name && it.fromLocation == it.toLocation}) {
                for((variable, equation) in update) {
                    result.appendln("${config.getIndent(3)}${variable}_u = ${Utils.generateCodeForParseTreeItem(equation)};")
                }

                if(update.isNotEmpty())
                    result.appendln()
            }

            result.appendln("${config.getIndent(3)}break;")
        }

        result.appendln("${config.getIndent(1)}}")
        result.appendln()

        result.appendln("${config.getIndent(1)}// Update from intermediary variables")
        result.appendln("${config.getIndent(1)}me->state = state_u;")

        result.append(Utils.performVariableFunctionForLocality(fsm, Locality.EXTERNAL_OUTPUT, CFileGenerator::updateFromIntermediateVariable, config))

        result.append(Utils.performVariableFunctionForLocality(fsm, Locality.INTERNAL, CFileGenerator::updateFromIntermediateVariable, config))

        return result.toString()
    }

    private fun generateIntermediateVariable(variable: Variable): String {
        return "${Utils.generateCType(variable.type)} ${variable.name}_u = me->${variable.name};"
    }

    private fun updateFromIntermediateVariable(variable: Variable): String {
        return "me->${variable.name} = ${variable.name}_u;"
    }
}