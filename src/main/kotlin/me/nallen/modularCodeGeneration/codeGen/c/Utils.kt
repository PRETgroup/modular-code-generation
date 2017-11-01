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
        when (item) {
            is And -> return padOperand(item, item.operandA) + " && " + padOperand(item, item.operandB)
            is Or -> return padOperand(item, item.operandA) + " || " + padOperand(item, item.operandB)
            is Not -> return "!" + padOperand(item, item.operandA)
            is GreaterThan -> return padOperand(item, item.operandA) + " > " + padOperand(item, item.operandB)
            is GreaterThanOrEqual -> return padOperand(item, item.operandA) + " >= " + padOperand(item, item.operandB)
            is LessThanOrEqual -> return padOperand(item, item.operandA) + " <= " + padOperand(item, item.operandB)
            is LessThan -> return padOperand(item, item.operandA) + " < " + padOperand(item, item.operandB)
            is Equal -> return padOperand(item, item.operandA) + " == " + padOperand(item, item.operandB)
            is NotEqual -> return padOperand(item, item.operandA) + " != " + padOperand(item, item.operandB)
            is Literal -> return item.value
            is me.nallen.modularCodeGeneration.parseTree.Variable -> return "me->${item.name}"
            is Plus -> return padOperand(item, item.operandA) + " + " + padOperand(item, item.operandB)
            is Minus -> return padOperand(item, item.operandA) + " - " + padOperand(item, item.operandB)
            is Negative -> return "-" + padOperand(item, item.operandA)
            is Multiply -> return padOperand(item, item.operandA) + " * " + padOperand(item, item.operandB)
            is Divide -> return padOperand(item, item.operandA) + " / " + padOperand(item, item.operandB)
            is SquareRoot -> return "sqrt(" + item.operandA.generateString() + ")"
        }
    }
}