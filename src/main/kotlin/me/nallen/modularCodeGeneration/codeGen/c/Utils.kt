package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.hybridAutomata.HybridAutomata
import me.nallen.modularCodeGeneration.hybridAutomata.Locality
import me.nallen.modularCodeGeneration.hybridAutomata.Variable
import me.nallen.modularCodeGeneration.parseTree.*
import me.nallen.modularCodeGeneration.utils.NamingConvention
import me.nallen.modularCodeGeneration.utils.convertWordDelimiterConvention

typealias ParseTreeLocality = me.nallen.modularCodeGeneration.parseTree.Locality

object Utils {
    val DEFAULT_CUSTOM_VARIABLES = mapOf("STEP_SIZE" to "STEP_SIZE")

    fun generateCType(type: VariableType?): String {
        return when(type) {
            null -> "void"
            VariableType.BOOLEAN -> "bool"
            VariableType.REAL -> "double"
        }
    }

    fun generatePrintfType(type: VariableType): String {
        return when(type) {
            VariableType.BOOLEAN -> "%c"
            VariableType.REAL -> "%f"
        }
    }

    fun performVariableFunctionForLocality(automata: HybridAutomata, locality: Locality, function: (v: Variable) -> String, config: Configuration = Configuration(), comment: String? = null, depth: Int = 1): String {
        val result = StringBuilder()

        if(automata.variables.any{it.locality == locality}) {
            result.appendln()
            if(comment != null)
                result.appendln("${config.getIndent(depth)}// $comment ${locality.getTextualName()}")

            for(variable in automata.variables
                    .filter{it.locality == locality}
                    .sortedBy { it.type }) {
                val output = function(variable)
                if(output.isNotEmpty())
                    result.appendln("${config.getIndent(depth)}$output")
            }
        }

        return result.toString()
    }

    fun createFolderName(vararg original: String): String {
        return original.convertWordDelimiterConvention(NamingConvention.UPPER_CAMEL_CASE)
    }

    fun createFileName(vararg original: String): String {
        return original.convertWordDelimiterConvention(NamingConvention.SNAKE_CASE)
    }

    fun createTypeName(vararg original: String): String {
        return original.convertWordDelimiterConvention(NamingConvention.UPPER_CAMEL_CASE)
    }

    fun createVariableName(vararg original: String): String {
        return original.convertWordDelimiterConvention(NamingConvention.SNAKE_CASE)
    }

    fun createFunctionName(vararg original: String): String {
        return original.convertWordDelimiterConvention(NamingConvention.UPPER_CAMEL_CASE)
    }

    fun createMacroName(vararg original: String): String {
        return original.convertWordDelimiterConvention(NamingConvention.UPPER_SNAKE_CASE)
    }

    private fun padOperand(item: ParseTreeItem, operand: ParseTreeItem, prefixData: PrefixData): String {
        var precedence = item.getPrecedence()
        if((!operand.getCommutative() || !item.getCommutative()) && operand.getChildren().size > 1) {
            precedence--;
        }
        if(precedence < operand.getPrecedence())
            return "(" + generateCodeForParseTreeItem(operand, prefixData) + ")"

        // Special cases
        if(item is Or && operand is And)
            return "(" + generateCodeForParseTreeItem(operand, prefixData) + ")"

        return generateCodeForParseTreeItem(operand, prefixData, item)
    }

    fun generateCodeForParseTreeItem(item: ParseTreeItem, prefixData: PrefixData = PrefixData(""), parent: ParseTreeItem? = null): String {
        return when (item) {
            is And -> padOperand(item, item.operandA, prefixData) + " && " + padOperand(item, item.operandB, prefixData)
            is Or -> padOperand(item, item.operandA, prefixData) + " || " + padOperand(item, item.operandB, prefixData)
            is Not -> "!" + padOperand(item, item.operandA, prefixData)
            is GreaterThan -> padOperand(item, item.operandA, prefixData) + " > " + padOperand(item, item.operandB, prefixData)
            is GreaterThanOrEqual -> padOperand(item, item.operandA, prefixData) + " >= " + padOperand(item, item.operandB, prefixData)
            is LessThanOrEqual -> padOperand(item, item.operandA, prefixData) + " <= " + padOperand(item, item.operandB, prefixData)
            is LessThan -> padOperand(item, item.operandA, prefixData) + " < " + padOperand(item, item.operandB, prefixData)
            is Equal -> padOperand(item, item.operandA, prefixData) + " == " + padOperand(item, item.operandB, prefixData)
            is NotEqual -> padOperand(item, item.operandA, prefixData) + " != " + padOperand(item, item.operandB, prefixData)
            is FunctionCall -> {
                val builder = StringBuilder()

                if(prefixData.requireSelfReferenceInFunctionCalls)
                    builder.append("me")

                for(argument in item.arguments) {
                    if(builder.isNotEmpty()) builder.append(", ")
                    builder.append(generateCodeForParseTreeItem(argument, prefixData))
                }

                return "${Utils.createFunctionName(item.functionName)}(${builder.toString()})"
            }
            is Literal -> item.value
            is me.nallen.modularCodeGeneration.parseTree.Variable -> {
                if(item.value != null)
                    padOperand(parent ?: item, item.value!!, prefixData)
                else
                    if(prefixData.customVariableNames.containsKey(item.name))
                        prefixData.customVariableNames[item.name]!!
                    else
                        "${prefixData.prefix}${Utils.createVariableName(item.name)}"
            }
            is Plus -> padOperand(item, item.operandA, prefixData) + " + " + padOperand(item, item.operandB, prefixData)
            is Minus -> padOperand(item, item.operandA, prefixData) + " - " + padOperand(item, item.operandB, prefixData)
            is Negative -> "-" + padOperand(item, item.operandA, prefixData)
            is Multiply -> padOperand(item, item.operandA, prefixData) + " * " + padOperand(item, item.operandB, prefixData)
            is Divide -> padOperand(item, item.operandA, prefixData) + " / " + padOperand(item, item.operandB, prefixData)
            is SquareRoot -> "sqrt(" + generateCodeForParseTreeItem(item.operandA, prefixData) + ")"
            is Exponential -> "exp(" + generateCodeForParseTreeItem(item.operandA, prefixData) + ")"
        }
    }

    fun generateCodeForProgram(program: Program, config: Configuration, depth: Int = 0, prefixData: PrefixData = PrefixData("")): String {
        val builder = StringBuilder()

        for(input in program.variables.filter({it.locality == ParseTreeLocality.INTERNAL})) {
            if(!prefixData.customVariableNames.containsKey(input.name))
                builder.appendln("${config.getIndent(depth)}${Utils.generateCType(input.type)} ${Utils.createVariableName(input.name)};")
        }
        if(builder.isNotEmpty())
            builder.appendln()

        for(line in program.lines) {
            val lineString = when(line) {
                is Statement -> "${Utils.generateCodeForParseTreeItem(line.logic, prefixData)};"
                is Assignment -> "${Utils.generateCodeForParseTreeItem(line.variableName, prefixData)} = ${Utils.generateCodeForParseTreeItem(line.variableValue, prefixData)};"
                is Return -> "return ${Utils.generateCodeForParseTreeItem(line.logic, prefixData)};"
                is IfStatement -> "if(${Utils.generateCodeForParseTreeItem(line.condition, prefixData)}) {\n${Utils.generateCodeForProgram(line.body, config, 1, prefixData)}\n}"
                is ElseIfStatement -> "else if(${Utils.generateCodeForParseTreeItem(line.condition, prefixData)}) {\n${Utils.generateCodeForProgram(line.body, config, 1, prefixData)}\n}"
                is ElseStatement -> "else {\n${Utils.generateCodeForProgram(line.body, config, 1, prefixData)}\n}"
            }

            builder.appendln(lineString.prependIndent(config.getIndent(depth)))
        }

        return builder.toString().trimEnd()
    }

    data class PrefixData(
            val prefix: String,
            val requireSelfReferenceInFunctionCalls: Boolean = false,
            val customVariableNames: Map<String, String> = DEFAULT_CUSTOM_VARIABLES
    )
}