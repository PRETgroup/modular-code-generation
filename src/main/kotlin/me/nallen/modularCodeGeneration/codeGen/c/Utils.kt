package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.finiteStateMachine.FiniteStateMachine
import me.nallen.modularCodeGeneration.finiteStateMachine.Locality
import me.nallen.modularCodeGeneration.finiteStateMachine.Variable
import me.nallen.modularCodeGeneration.finiteStateMachine.VariableType
import me.nallen.modularCodeGeneration.parseTree.*

object Utils {
    fun generateCType(type: VariableType): String {
        when(type) {
            VariableType.BOOLEAN -> return "bool"
            VariableType.REAL -> return "double"
        }
    }

    fun performVariableFunctionForLocality(fsm: FiniteStateMachine, locality: Locality, function: (v: Variable) -> String, config: Configuration = Configuration(), comment: String? = null): String {
        val result = StringBuilder()

        if(fsm.variables.any{it.locality == locality}) {
            result.appendln()
            if(comment != null)
                result.appendln("${config.getIndent(1)}// $comment ${locality.getTextualName()}")

            for(variable in fsm.variables
                    .filter{it.locality == locality}
                    .sortedBy { it.type }) {
                result.appendln("${config.getIndent(1)}${function(variable)}")
            }
        }

        return result.toString()
    }

    fun padOperand(item: ParseTreeItem, operand: ParseTreeItem): String {
        if(item.getPrecedence() < operand.getPrecedence())
            return "(" + generateCodeForParseTreeItem(operand) + ")"

        return generateCodeForParseTreeItem(operand)
    }

    fun generateCodeForParseTreeItem(item: ParseTreeItem): String {
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
                    padOperand(item, item.value!!)
                else
                    "me->${item.name}"
            }
            is Plus -> padOperand(item, item.operandA) + " + " + padOperand(item, item.operandB)
            is Minus -> padOperand(item, item.operandA) + " - " + padOperand(item, item.operandB)
            is Negative -> "-" + padOperand(item, item.operandA)
            is Multiply -> padOperand(item, item.operandA) + " * " + padOperand(item, item.operandB)
            is Divide -> padOperand(item, item.operandA) + " / " + padOperand(item, item.operandB)
            is SquareRoot -> "sqrt(" + item.operandA.generateString() + ")"
        }
    }
}