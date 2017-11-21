package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.CodeGenManager
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.ParametrisationMethod
import me.nallen.modularCodeGeneration.codeGen.SaturationDirection
import me.nallen.modularCodeGeneration.hybridAutomata.*
import me.nallen.modularCodeGeneration.parseTree.Literal
import me.nallen.modularCodeGeneration.parseTree.Multiply
import me.nallen.modularCodeGeneration.parseTree.Plus
import me.nallen.modularCodeGeneration.parseTree.VariableType

import me.nallen.modularCodeGeneration.parseTree.Variable as ParseTreeVariable

object CFileGenerator {
    private var automata: HybridAutomata = HybridAutomata("Temp")
    private var config: Configuration = Configuration()

    private var requireSelfReferenceInFunctionCalls: Boolean = false
    private val delayedVariableTypes: HashMap<String, VariableType> = HashMap<String, VariableType>()

    fun generate(automata: HybridAutomata, config: Configuration = Configuration()): String {
        this.automata = automata
        this.config = config

        this.requireSelfReferenceInFunctionCalls = config.parametrisationMethod == ParametrisationMethod.RUN_TIME
        this.delayedVariableTypes.clear()
        for(variable in automata.variables.filter({it.delayableBy > 0}))
            this.delayedVariableTypes.put(variable.name, variable.type)

        val result = StringBuilder()

        result.appendln("#include \"${Utils.createFileName(automata.name)}.h\"")
        result.appendln()

        if(automata.functions.size > 0)
            result.appendln(generateCustomFunctions())

        if(config.parametrisationMethod == ParametrisationMethod.RUN_TIME)
            result.appendln(generateParametrisationFunction())

        result.appendln(generateInitialisationFunction())

        result.appendln(generateExecutionFunction())

        return result.toString().trim()
    }

    private fun generateCustomFunctions(): String {
        val result = StringBuilder()

        for(function in automata.functions) {
            result.appendln(generateCustomFunction(function))
        }

        return result.toString()
    }

    private fun generateCustomFunction(function: FunctionDefinition): String {
        val result = StringBuilder()

        result.append("static ${Utils.generateCType(function.returnType)} ${Utils.createFunctionName(function.name)}(")
        var first = true
        if(config.parametrisationMethod == ParametrisationMethod.RUN_TIME) {
            first = false
            result.append("${Utils.createTypeName(automata.name)}* me")
        }
        for(input in function.inputs) {
            if(!first)
                result.append(", ")
            first = false

            result.append("${Utils.generateCType(input.type)} ${Utils.createVariableName(input.name)}")
        }
        result.appendln(") {")

        val customDefinedVariables = LinkedHashMap<String, String>(Utils.DEFAULT_CUSTOM_VARIABLES)
        for(parameter in automata.variables.filter({it.locality == Locality.PARAMETER})) {
            customDefinedVariables.put(parameter.name, "me->${Utils.createVariableName(parameter.name)}")
        }

        result.appendln(Utils.generateCodeForProgram(function.logic, config, 1, Utils.PrefixData("", requireSelfReferenceInFunctionCalls, delayedVariableTypes, customDefinedVariables)))

        result.appendln("}")

        return result.toString()
    }

    private fun generateParametrisationFunction(): String {
        val result = StringBuilder()

        result.appendln("// ${automata.name} Default Parametrisation function")
        result.appendln("void ${Utils.createFunctionName(automata.name, "Parametrise")}(${Utils.createTypeName(automata.name)}* me) {")

        if(automata.variables.any({it.locality == Locality.PARAMETER && it.defaultValue != null}))
            result.append(Utils.performVariableFunctionForLocality(automata, Locality.PARAMETER, CFileGenerator::generateParameterInitialisation, config, "Initialise Default"))

        result.appendln("}")

        return result.toString()
    }

    private fun generateInitialisationFunction(): String {
        val result = StringBuilder()

        result.appendln("// ${automata.name} Initialisation function")
        result.appendln("void ${Utils.createFunctionName(automata.name, "Init")}(${Utils.createTypeName(automata.name)}* me) {")

        result.appendln("${config.getIndent(1)}// Initialise State")
        result.appendln("${config.getIndent(1)}me->state = ${Utils.createMacroName(automata.name, automata.init.state)};")

        result.append(Utils.performVariableFunctionForLocality(automata, Locality.EXTERNAL_OUTPUT, CFileGenerator::generateVariableInitialisation, config, "Initialise"))

        result.append(Utils.performVariableFunctionForLocality(automata, Locality.INTERNAL, CFileGenerator::generateVariableInitialisation, config, "Initialise"))

        if(automata.variables.any({it.delayableBy > 0})) {
            result.appendln()
            result.append("${config.getIndent(1)}// Initialise Delayed Variables")

            for(variable in automata.variables
                    .filter{it.delayableBy > 0}) {
                result.appendln()
                result.appendln("${config.getIndent(1)}(void) memset((void *)&me->${Utils.createVariableName(variable.name, "delayed")}, 0, sizeof(${Utils.createTypeName("Delayable", Utils.generateCType(variable.type))}));")
                result.appendln("${config.getIndent(1)}${Utils.createFunctionName("Delayable", Utils.generateCType(variable.type), "Init")}(&me->${Utils.createVariableName(variable.name, "delayed")}, ${variable.delayableBy});")
            }
        }

        result.appendln("}")

        return result.toString()
    }

    private fun generateVariableInitialisation(variable: Variable): String {
        var initValue: String = generateDefaultInitForType(variable.type)

        if(automata.init.valuations.containsKey(variable.name)) {
            initValue = Utils.generateCodeForParseTreeItem(automata.init.valuations[variable.name] !!, Utils.PrefixData("me->", requireSelfReferenceInFunctionCalls, delayedVariableTypes))
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
            return "me->${Utils.createVariableName(variable.name)} = ${Utils.generateCodeForParseTreeItem(variable.defaultValue!!, Utils.PrefixData("me->", requireSelfReferenceInFunctionCalls, delayedVariableTypes))};"
        }

        return ""
    }


    private fun generateExecutionFunction(): String {
        val result = StringBuilder()

        result.appendln("// ${automata.name} Execution function")
        result.appendln("void ${Utils.createFunctionName(automata.name, "Run")}(${Utils.createTypeName(automata.name)}* me) {")

        result.appendln("${config.getIndent(1)}// Create intermediary variables")
        result.appendln("${config.getIndent(1)}enum ${Utils.createTypeName(automata.name, "States")} state_u = me->state;")

        result.append(Utils.performVariableFunctionForLocality(automata, Locality.EXTERNAL_OUTPUT, CFileGenerator::generateIntermediateVariable, config))

        result.append(Utils.performVariableFunctionForLocality(automata, Locality.INTERNAL, CFileGenerator::generateIntermediateVariable, config))
        result.appendln()

        if(automata.variables.any({it.delayableBy > 0})) {
            for(variable in automata.variables.filter({it.delayableBy > 0}))
                result.appendln("${config.getIndent(1)}${Utils.createFunctionName("Delayable", Utils.generateCType(variable.type), "Add")}(&me->${Utils.createVariableName(variable.name, "delayed")}, me->${Utils.createVariableName(variable.name)});")
            result.appendln()
        }

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

        result.append(Utils.performVariableFunctionForLocality(automata, Locality.EXTERNAL_OUTPUT, CFileGenerator::updateFromIntermediateVariable, config, depth=defaultIndent))

        result.append(Utils.performVariableFunctionForLocality(automata, Locality.INTERNAL, CFileGenerator::updateFromIntermediateVariable, config, depth=defaultIndent))

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

        for(location in automata.locations) {
            result.appendln("${config.getIndent(defaultIndent+1)}case ${Utils.createMacroName(automata.name, location.name)}: // Logic for state ${location.name}")

            var atLeastOneIf = false
            if(!config.requireOneIntraTransitionPerTick) {
                result.appendln("${config.getIndent(defaultIndent+2)}if(${Utils.generateCodeForParseTreeItem(location.invariant, Utils.PrefixData("me->", requireSelfReferenceInFunctionCalls, delayedVariableTypes))}) {")

                result.append(generateCodeForIntraLogic(location, defaultIndent+3))

                result.appendln("${config.getIndent(defaultIndent+3)}// Remain in this state")
                result.appendln("${config.getIndent(defaultIndent+3)}state_u = ${Utils.createMacroName(automata.name, location.name)};")

                if(countTransitions) {
                    result.appendln()
                    result.appendln("${config.getIndent(defaultIndent+3)}// Taking an intra-location transition stops execution")
                    result.appendln("${config.getIndent(defaultIndent+3)}remaining_transitions = 0;")
                }

                result.appendln("${config.getIndent(defaultIndent+2)}}")

                atLeastOneIf = true
            }

            for((_, toLocation, guard, update) in automata.edges.filter{it.fromLocation == location.name }) {
                result.appendln("${config.getIndent(defaultIndent+2)}${if(atLeastOneIf) { "else " } else { "" }}if(${Utils.generateCodeForParseTreeItem(guard, Utils.PrefixData("me->", requireSelfReferenceInFunctionCalls, delayedVariableTypes))}) {")

                for((variable, equation) in update) {
                    result.appendln("${config.getIndent(defaultIndent+3)}${Utils.createVariableName(variable)}_u = ${Utils.generateCodeForParseTreeItem(equation, Utils.PrefixData("me->", requireSelfReferenceInFunctionCalls, delayedVariableTypes))};")
                }

                if(update.isNotEmpty())
                    result.appendln()

                result.appendln("${config.getIndent(defaultIndent+3)}// Next state is $toLocation")
                result.appendln("${config.getIndent(defaultIndent+3)}state_u = ${Utils.createMacroName(automata.name, toLocation)};")

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

        for(location in automata.locations) {
            result.appendln("${config.getIndent(2)}case ${Utils.createMacroName(automata.name, location.name)}: // Intra-location logic for state ${location.name}")

            result.append(generateCodeForIntraLogic(location, 3))

            result.appendln("${config.getIndent(3)}break;")
        }

        result.appendln("${config.getIndent(1)}}")
        result.appendln()

        result.appendln("${config.getIndent(1)}// Update from intermediary variables")
        result.append(Utils.performVariableFunctionForLocality(automata, Locality.EXTERNAL_OUTPUT, CFileGenerator::updateFromIntermediateVariable, config))

        result.append(Utils.performVariableFunctionForLocality(automata, Locality.INTERNAL, CFileGenerator::updateFromIntermediateVariable, config))

        return result.toString()
    }

    private fun generateCodeForIntraLogic(location: Location, indent: Int): String {
        val result = StringBuilder()

        val customVars = HashMap<String, String>(Utils.DEFAULT_CUSTOM_VARIABLES)

        for((variable, equation) in location.flow) {
            val eulerSolution = Plus(ParseTreeVariable(variable), Multiply(equation, ParseTreeVariable("STEP_SIZE")))
            result.appendln("${config.getIndent(indent)}${Utils.createVariableName(variable)}_u = ${Utils.generateCodeForParseTreeItem(eulerSolution, Utils.PrefixData("me->", requireSelfReferenceInFunctionCalls, delayedVariableTypes))};")
            customVars.put(variable, "${Utils.createVariableName(variable)}_u")
        }

        if(location.flow.isNotEmpty())
            result.appendln()

        for((variable, equation) in location.update) {
            result.appendln("${config.getIndent(indent)}${Utils.createVariableName(variable)}_u = ${Utils.generateCodeForParseTreeItem(equation, Utils.PrefixData("me->", requireSelfReferenceInFunctionCalls, delayedVariableTypes, customVars))};")
        }

        if(location.update.isNotEmpty())
            result.appendln()

        val saturationLimits = CodeGenManager.collectSaturationLimits(location, automata.edges)

        for((point, dependencies) in saturationLimits) {
            val variable = Utils.createVariableName(point.variable)
            val limit = Utils.generateCodeForParseTreeItem(point.value, Utils.PrefixData("me->", requireSelfReferenceInFunctionCalls, delayedVariableTypes))
            val condition = when(point.direction) {
                SaturationDirection.RISING -> "${variable}_u > $limit && me->$variable < $limit"
                SaturationDirection.FALLING -> "${variable}_u < $limit && me->$variable > $limit"
                SaturationDirection.BOTH -> "(${variable}_u > $limit && me->$variable < $limit) || (${variable}_u < $limit && me->$variable > $limit)"
            }
            result.appendln("${config.getIndent(indent)}if($condition) {")
            result.appendln("${config.getIndent(indent+1)}// Need to saturate ${point.variable} to $limit")

            if(dependencies.isNotEmpty()) {
                result.appendln("${config.getIndent(indent+1)}// Also some dependencies that need saturating")
                result.appendln("${config.getIndent(indent+1)}double frac = ($limit - me->$variable) / (${variable}_u - me->$variable);")
                result.appendln()
                for(dependency in dependencies) {
                    val dependencyVariable = Utils.createVariableName(dependency)
                    result.appendln("${config.getIndent(indent+1)}${dependencyVariable}_u = me->$dependencyVariable + frac * (${dependencyVariable}_u - me->$dependencyVariable);")
                }
                result.appendln()
            }

            result.appendln("${config.getIndent(indent+1)}${variable}_u = $limit;")
            result.appendln("${config.getIndent(indent)}}")
        }

        if(saturationLimits.isNotEmpty())
            result.appendln()

        return result.toString()
    }

    private fun generateIntermediateVariable(variable: Variable): String {
        return "${Utils.generateCType(variable.type)} ${Utils.createVariableName(variable.name)}_u = me->${Utils.createVariableName(variable.name)};"
    }

    private fun updateFromIntermediateVariable(variable: Variable): String {
        return "me->${Utils.createVariableName(variable.name)} = ${Utils.createVariableName(variable.name)}_u;"
    }
}