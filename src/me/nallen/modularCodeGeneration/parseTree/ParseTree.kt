package me.nallen.modularCodeGeneration.parseTree

/**
 * Created by nall426 on 1/06/2017.
 */

sealed class ParseTreeItem()

data class And(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem()
data class Or(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem()
data class Not(var operandA: ParseTreeItem): ParseTreeItem()

data class GreaterThanOrEqual(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem()
data class GreaterThan(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem()
data class LessThanOrEqual(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem()
data class LessThan(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem()
data class Equal(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem()
data class NotEqual(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem()

data class Plus(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem()

data class Literal(var name: String): ParseTreeItem()


fun GenerateParseTreeFromString(input: String): ParseTreeItem {
    println(input)

    val postfix = convertToPostfix(input)
    println(postfix)

    val arguments = postfix.split(" ")
    val stack = ArrayList<ParseTreeItem>()

    for(argument in arguments) {
        if(argument.isBlank())
            continue

        val operand = getOperandForSequence(argument)

        if(operand != null) {
            val operator = operands[operand]
            if(operator != null) {
                var item = when(operand) {
                    Operand.AND -> And(stack[stack.size-2], stack[stack.size-1])
                    Operand.OR -> Or(stack[stack.size-2], stack[stack.size-1])
                    Operand.NOT -> Not(stack[stack.size-1])
                    Operand.GREATER_THAN_OR_EQUAL -> GreaterThanOrEqual(stack[stack.size-2], stack[stack.size-1])
                    Operand.GREATER_THAN -> GreaterThan(stack[stack.size-2], stack[stack.size-1])
                    Operand.LESS_THAN_OR_EQUAL -> LessThanOrEqual(stack[stack.size-2], stack[stack.size-1])
                    Operand.LESS_THAN -> LessThan(stack[stack.size-2], stack[stack.size-1])
                    Operand.EQUAL -> Equal(stack[stack.size-2], stack[stack.size-1])
                    Operand.NOT_EQUAL -> NotEqual(stack[stack.size-2], stack[stack.size-1])
                    Operand.OPEN_BRACKET -> null
                    Operand.CLOSE_BRACKET -> null
                    Operand.PLUS -> Plus(stack[stack.size-2], stack[stack.size-1])
                }

                if(item != null) {
                    for(i in 1..operator.operands)
                        stack.removeAt(stack.size-1)

                    stack.add(item)
                }
            }
        }
        else {
            stack.add(Literal(argument))
        }
    }

    if(stack.size != 1)
        throw IllegalArgumentException("Invalid formula provided: $input")

    return stack[0]
}

fun ParseTreeItem.padOperand(operand: ParseTreeItem): String {
    if(this.getPrecedence() < operand.getPrecedence())
        return "(" + operand.generateString() + ")"

    return operand.generateString();
}

fun ParseTreeItem.getPrecedence(): Int {
    val operand = when(this) {
        is And -> Operand.AND
        is Or -> Operand.OR
        is Not -> Operand.NOT
        is GreaterThan -> Operand.GREATER_THAN
        is GreaterThanOrEqual -> Operand.GREATER_THAN_OR_EQUAL
        is LessThanOrEqual -> Operand.LESS_THAN_OR_EQUAL
        is LessThan -> Operand.LESS_THAN
        is Equal -> Operand.EQUAL
        is NotEqual -> Operand.NOT_EQUAL
        is Literal -> return 0
        is Plus -> Operand.PLUS
    }

    val operator = operands[operand]
    if(operator != null)
        return operator.precedence

    return 0
}

fun ParseTreeItem.generateString(): String {
    when (this) {
        is And -> return this.padOperand(operandA) + " && " + this.padOperand(operandB)
        is Or -> return this.padOperand(operandA) + " || " + this.padOperand(operandB)
        is Not -> return "!" + this.padOperand(operandA)
        is GreaterThan -> return this.padOperand(operandA) + " > " + this.padOperand(operandB)
        is GreaterThanOrEqual -> return this.padOperand(operandA) + " >= " + this.padOperand(operandB)
        is LessThanOrEqual -> return this.padOperand(operandA) + " <= " + this.padOperand(operandB)
        is LessThan -> return this.padOperand(operandA) + " < " + this.padOperand(operandB)
        is Equal -> return this.padOperand(operandA) + " == " + this.padOperand(operandB)
        is NotEqual -> return this.padOperand(operandA) + " != " + this.padOperand(operandB)
        is Literal -> return this.name
        is Plus -> return this.padOperand(operandA) + " + " + this.padOperand(operandB)
    }
}
