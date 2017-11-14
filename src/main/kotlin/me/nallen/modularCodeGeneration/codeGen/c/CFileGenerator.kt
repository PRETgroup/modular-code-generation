package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.ParametrisationMethod
import me.nallen.modularCodeGeneration.finiteStateMachine.*
import me.nallen.modularCodeGeneration.finiteStateMachine.Variable
import me.nallen.modularCodeGeneration.parseTree.Literal
import me.nallen.modularCodeGeneration.parseTree.VariableType

object CFileGenerator {
    private var fsm: FiniteStateMachine = FiniteStateMachine("Temp")
    private var config: Configuration = Configuration()

    private var requireSelfReferenceInFunctionCalls: Boolean = false

    fun generate(fsm: FiniteStateMachine, config: Configuration = Configuration()): String {
        this.fsm = fsm
        this.config = config

        this.requireSelfReferenceInFunctionCalls = config.parametrisationMethod == ParametrisationMethod.RUN_TIME

        val result = StringBuilder()

        result.appendln("#include \"${Utils.createFileName(fsm.name)}.h\"")
        result.appendln()

        if(fsm.functions.size > 0)
            result.appendln(generateCustomFunctions())

        result.appendln(generateInitialisationFunction())

        result.appendln(generateExecutionFunction())

        return result.toString().trim()
    }

    private fun generateCustomFunctions(): String {
        val result = StringBuilder()

        for(function in fsm.functions) {
            result.appendln(generateCustomFunction(fsm, function))
        }

        return result.toString()
    }

    private fun generateCustomFunction(fsm: FiniteStateMachine, function: FunctionDefinition): String {
        val result = StringBuilder()

        result.append("static void ${Utils.createFunctionName(function.name)}(")
        var first = true
        if(config.parametrisationMethod == ParametrisationMethod.RUN_TIME) {
            first = false
            result.append("${Utils.createTypeName(fsm.name)}* me")
        }
        for(input in function.inputs) {
            if(!first)
                result.append(", ")
            first = false

            result.append("${Utils.generateCType(input.type)} ${Utils.createVariableName(input.name)}")
        }
        result.appendln(") {")

        val customDefinedVariables = LinkedHashMap<String, String>(Utils.DEFAULT_CUSTOM_VARIABLES)
        for(parameter in fsm.variables.filter({it.locality == Locality.PARAMETER})) {
            customDefinedVariables.put(parameter.name, "me->${Utils.createVariableName(parameter.name)}")
        }

        result.appendln(Utils.generateCodeForProgram(function.logic, config, 1, Utils.PrefixData("", requireSelfReferenceInFunctionCalls, customDefinedVariables)))

        result.appendln("}")

        return result.toString()
    }

    private fun generateInitialisationFunction(): String {
        val result = StringBuilder()

        result.appendln("// ${fsm.name} Initialisation function")
        result.appendln("void ${Utils.createFunctionName(fsm.name, "Init")}(${Utils.createTypeName(fsm.name)}* me) {")

        result.appendln("${config.getIndent(1)}// Initialise State")
        result.appendln("${config.getIndent(1)}me->state = ${Utils.createMacroName(fsm.name, fsm.init.state)};")

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
            initValue = Utils.generateCodeForParseTreeItem(fsm.init.valuations[variable.name] !!, Utils.PrefixData("me->", requireSelfReferenceInFunctionCalls))
        }

        return "me->${Utils.createVariableName(variable.name)} = $initValue;"
    }

    private fun generateDefaultInitForType(type: VariableType): String {
        when(type) {
            VariableType.BOOLEAN -> return Utils.generateCodeForParseTreeItem(Literal("false"))
            VariableType.REAL -> return Utils.generateCodeForParseTreeItem(Literal("0"))
        }
    }

    private fun generateParameterInitialisation(variable: Variable): String {
        if(variable.defaultValue != null) {
            return "me->${Utils.createVariableName(variable.name)} = ${Utils.generateCodeForParseTreeItem(variable.defaultValue!!, Utils.PrefixData("me->", requireSelfReferenceInFunctionCalls))};"
        }

        return ""
    }


    private fun generateExecutionFunction(): String {
        val result = StringBuilder()

        result.appendln("// ${fsm.name} Execution function")
        result.appendln("void ${Utils.createFunctionName(fsm.name, "Run")}(${Utils.createTypeName(fsm.name)}* me) {")

        result.appendln("${config.getIndent(1)}// Create intermediary variables")
        result.appendln("${config.getIndent(1)}enum ${Utils.createTypeName(fsm.name, "States")} state_u = me->state;")

        result.append(Utils.performVariableFunctionForLocality(fsm, Locality.EXTERNAL_OUTPUT, CFileGenerator::generateIntermediateVariable, config))

        result.append(Utils.performVariableFunctionForLocality(fsm, Locality.INTERNAL, CFileGenerator::generateIntermediateVariable, config))
        result.appendln()


        val needsTransitionCounting = config.maximumInterTransitions > 1
        val defaultIndent = if(needsTransitionCounting) 2 else 1

        if(needsTransitionCounting) {
            result.appendln("${config.getIndent(1)}unsigned int remaining_transitions = ${config.maximumInterTransitions};")
            result.appendln("${config.getIndent(1)}while(remaining_transitions > 0) {")
            result.appendln("${config.getIndent(2)}// Decrement the remaining transitions available")
            result.appendln("${config.getIndent(2)}remaining_transitions--;")
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
            result.appendln("${config.getIndent(defaultIndent+1)}case ${Utils.createMacroName(fsm.name, name)}: // Logic for state $name")

            var atLeastOneIf = false
            for((fromLocation, toLocation, guard, update) in fsm.transitions.filter{it.fromLocation == name && (!config.requireOneIntraTransitionPerTick || it.fromLocation != it.toLocation) }) {
                result.appendln("${config.getIndent(defaultIndent+2)}${if(atLeastOneIf) { "else " } else { "" }}if(${Utils.generateCodeForParseTreeItem(guard, Utils.PrefixData("me->", requireSelfReferenceInFunctionCalls))}) {")

                for((variable, equation) in update) {
                    result.appendln("${config.getIndent(defaultIndent+3)}${Utils.createVariableName(variable)}_u = ${Utils.generateCodeForParseTreeItem(equation, Utils.PrefixData("me->", requireSelfReferenceInFunctionCalls))};")
                }

                if(update.isNotEmpty())
                    result.appendln()

                result.appendln("${config.getIndent(defaultIndent+3)}// Next state is $toLocation")
                result.appendln("${config.getIndent(defaultIndent+3)}state_u = ${Utils.createMacroName(fsm.name, toLocation)};")

                if(countTransitions && toLocation == fromLocation) {
                    result.appendln()
                    result.appendln("${config.getIndent(defaultIndent+3)}// Taking an intra-location transition stops execution")
                    result.appendln("${config.getIndent(defaultIndent+3)}remaining_transitions = 0;")
                }

                result.appendln("${config.getIndent(defaultIndent+2)}}")

                atLeastOneIf = true
            }

            if(atLeastOneIf && countTransitions) {
                result.appendln("${config.getIndent(defaultIndent+2)}else {")
                result.appendln("${config.getIndent(defaultIndent+3)}// No available transition stops execution")
                result.appendln("${config.getIndent(defaultIndent+3)}remaining_transitions = 0;")
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
            result.appendln("${config.getIndent(2)}case ${Utils.createMacroName(fsm.name, name)}: // Intra-location logic for state $name")

            for((_, _, _, update) in fsm.transitions.filter{it.fromLocation == name && it.fromLocation == it.toLocation}) {
                for((variable, equation) in update) {
                    result.appendln("${config.getIndent(3)}${Utils.createVariableName(variable)}_u = ${Utils.generateCodeForParseTreeItem(equation, Utils.PrefixData("me->", requireSelfReferenceInFunctionCalls = requireSelfReferenceInFunctionCalls))};")
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
        return "${Utils.generateCType(variable.type)} ${Utils.createVariableName(variable.name)}_u = me->${Utils.createVariableName(variable.name)};"
    }

    private fun updateFromIntermediateVariable(variable: Variable): String {
        return "me->${Utils.createVariableName(variable.name)} = ${Utils.createVariableName(variable.name)}_u;"
    }
}