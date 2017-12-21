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
            VariableType.BOOLEAN -> "%d"
            VariableType.REAL -> "%f"
        }
    }

    fun performVariableFunctionForLocality(automata: HybridAutomata, locality: Locality, function: (v: Variable) -> String, config: Configuration = Configuration(), comment: String? = null, depth: Int = 1): String {
        val result = StringBuilder()

        if(automata.variables.any{it.locality == locality}) {
            result.appendln()
            if(comment != null)
                result.appendln("${config.getIndent(depth)}// $comment ${locality.getTextualName()}")

            automata.variables
                    .filter{it.locality == locality}
                    .sortedBy { it.type }
                    .map { function(it) }
                    .filter { it.isNotEmpty() }
                    .forEach { result.appendln("${config.getIndent(depth)}$it") }
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
            precedence--
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
                if(item.functionName == "delayed") {
                    if(item.arguments.size == 2) {
                        if(item.arguments[0] is me.nallen.modularCodeGeneration.parseTree.Variable) {
                            val varName = item.arguments[0].getString()
                            if(prefixData.delayedVariableTypes.containsKey(varName)) {
                                return "${createFunctionName("Delayable", generateCType(prefixData.delayedVariableTypes[varName]), "Get")}(" +
                                        "&${generateCodeForParseTreeItem(item.arguments[0], prefixData)}_delayed, " +
                                        "${generateCodeForParseTreeItem(item.arguments[1], prefixData)})"
                            }
                        }
                    }
                }

                val builder = StringBuilder()

                if(prefixData.requireSelfReferenceInFunctionCalls)
                    builder.append("me")

                for(argument in item.arguments) {
                    if(builder.isNotEmpty()) builder.append(", ")
                    builder.append(generateCodeForParseTreeItem(argument, prefixData))
                }

                return "${Utils.createFunctionName(item.functionName)}($builder)"
            }
            is Literal -> item.value
            is me.nallen.modularCodeGeneration.parseTree.Variable -> {
                if(item.value != null)
                    return padOperand(parent ?: item, item.value!!, prefixData)
                else {
                    val parts = item.name.split(".")
                    val builder = StringBuilder()

                    for(part in parts) {
                        if(builder.isNotEmpty()) builder.append(".")
                        if(prefixData.customVariableNames.containsKey(part))
                            builder.append(prefixData.customVariableNames[part]!!)
                        else
                            builder.append("${prefixData.prefix}${Utils.createVariableName(part)}")
                    }

                    return builder.toString()
                }
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

        program.variables.filter({it.locality == ParseTreeLocality.INTERNAL})
                .filterNot { prefixData.customVariableNames.containsKey(it.name) }
                .forEach { builder.appendln("${config.getIndent(depth)}${Utils.generateCType(it.type)} ${Utils.createVariableName(it.name)};") }
        if(builder.isNotEmpty())
            builder.appendln()

        program.lines
                .map {
                    when(it) {
                        is Statement -> "${Utils.generateCodeForParseTreeItem(it.logic, prefixData)};"
                        is Assignment -> "${Utils.generateCodeForParseTreeItem(it.variableName, prefixData)} = ${Utils.generateCodeForParseTreeItem(it.variableValue, prefixData)};"
                        is Return -> "return ${Utils.generateCodeForParseTreeItem(it.logic, prefixData)};"
                        is IfStatement -> "if(${Utils.generateCodeForParseTreeItem(it.condition, prefixData)}) {\n${Utils.generateCodeForProgram(it.body, config, 1, prefixData)}\n}"
                        is ElseIfStatement -> "else if(${Utils.generateCodeForParseTreeItem(it.condition, prefixData)}) {\n${Utils.generateCodeForProgram(it.body, config, 1, prefixData)}\n}"
                        is ElseStatement -> "else {\n${Utils.generateCodeForProgram(it.body, config, 1, prefixData)}\n}"
                    }
                }
                .forEach { builder.appendln(it.prependIndent(config.getIndent(depth))) }

        return builder.toString().trimEnd()
    }

    data class PrefixData(
            val prefix: String,
            val requireSelfReferenceInFunctionCalls: Boolean = false,
            val delayedVariableTypes: Map<String, VariableType> = HashMap(),
            val customVariableNames: Map<String, String> = DEFAULT_CUSTOM_VARIABLES
    )
}