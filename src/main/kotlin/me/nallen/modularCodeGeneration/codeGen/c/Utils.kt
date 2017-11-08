package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.finiteStateMachine.FiniteStateMachine
import me.nallen.modularCodeGeneration.finiteStateMachine.Locality
import me.nallen.modularCodeGeneration.finiteStateMachine.Variable
import me.nallen.modularCodeGeneration.finiteStateMachine.VariableType
import me.nallen.modularCodeGeneration.parseTree.*
import me.nallen.modularCodeGeneration.utils.NamingConvention
import me.nallen.modularCodeGeneration.utils.convertWordDelimiterConvention

object Utils {
    fun generateCType(type: VariableType): String {
        return when(type) {
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

    fun performVariableFunctionForLocality(fsm: FiniteStateMachine, locality: Locality, function: (v: Variable) -> String, config: Configuration = Configuration(), comment: String? = null, depth: Int = 1): String {
        val result = StringBuilder()

        if(fsm.variables.any{it.locality == locality}) {
            result.appendln()
            if(comment != null)
                result.appendln("${config.getIndent(depth)}// $comment ${locality.getTextualName()}")

            for(variable in fsm.variables
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

    fun padOperand(item: ParseTreeItem, operand: ParseTreeItem): String {
        if(item.getPrecedence() < operand.getPrecedence())
            return "(" + generateCodeForParseTreeItem(operand) + ")"

        return generateCodeForParseTreeItem(operand, item)
    }

    fun generateCodeForParseTreeItem(item: ParseTreeItem, parent: ParseTreeItem? = null): String {
        return when (item) {
            is And -> padOperand(item, item.operandA) + " && " + padOperand(item, item.operandB)
            is Or -> padOperand(item, item.operandA) + " || " + padOperand(item, item.operandB)
            is Not -> "!" + padOperand(item, item.operandA)
            is GreaterThan -> padOperand(item, item.operandA) + " > " + padOperand(item, item.operandB)
            is GreaterThanOrEqual -> padOperand(item, item.operandA) + " >= " + padOperand(item, item.operandB)
            is LessThanOrEqual -> padOperand(item, item.operandA) + " <= " + padOperand(item, item.operandB)
            is LessThan -> padOperand(item, item.operandA) + " < " + padOperand(item, item.operandB)
            is Equal -> padOperand(item, item.operandA) + " == " + padOperand(item, item.operandB)
            is NotEqual -> padOperand(item, item.operandA) + " != " + padOperand(item, item.operandB)
            is Literal -> item.value
            is me.nallen.modularCodeGeneration.parseTree.Variable -> {
                if(item.value != null)
                    padOperand(parent ?: item, item.value!!)
                else
                    if(item.name == "STEP_SIZE")
                        "STEP_SIZE"
                    else
                        "me->${Utils.createVariableName(item.name)}"
            }
            is Plus -> padOperand(item, item.operandA) + " + " + padOperand(item, item.operandB)
            is Minus -> padOperand(item, item.operandA) + " - " + padOperand(item, item.operandB)
            is Negative -> "-" + padOperand(item, item.operandA)
            is Multiply -> padOperand(item, item.operandA) + " * " + padOperand(item, item.operandB)
            is Divide -> padOperand(item, item.operandA) + " / " + padOperand(item, item.operandB)
            is SquareRoot -> "sqrt(" + generateCodeForParseTreeItem(item.operandA) + ")"
        }
    }
}