package me.nallen.modularCodeGeneration.parseTree

/**
 * Created by nall426 on 1/06/2017.
 */

sealed class ParseTreeItem(var type: String) {
    companion object Factory {
        fun generate(input: String): ParseTreeItem = GenerateParseTreeFromString(input)
    }
}

data class And(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem("and")
data class Or(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem("or")
data class Not(var operandA: ParseTreeItem): ParseTreeItem("not")

data class GreaterThanOrEqual(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem("greaterThanOrEqualTo")
data class GreaterThan(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem("greaterThan")
data class LessThanOrEqual(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem("lessThanOrEqualTo")
data class LessThan(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem("lessThan")
data class Equal(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem("euqal")
data class NotEqual(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem("notEqual")

data class Plus(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem("plus")
data class Minus(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem("minus")
data class Multiply(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem("multiply")
data class Divide(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem("divide")

data class SquareRoot(var operandA: ParseTreeItem): ParseTreeItem("squareRoot")

data class Variable(var name: String): ParseTreeItem("variable")
data class Literal(var value: String): ParseTreeItem("literal")


fun GenerateParseTreeFromString(input: String): ParseTreeItem {
    val postfix = convertToPostfix(input)

    val arguments = postfix.split(" ")
    val stack = ArrayList<ParseTreeItem>()

    for(argument in arguments) {
        if(argument.isBlank())
            continue

        val operand = getOperandForSequence(argument)

        if(operand != null) {
            val operator = operands[operand]
            if(operator != null) {
                val item = when(operand) {
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
                    Operand.MINUS -> Minus(stack[stack.size-2], stack[stack.size-1])
                    Operand.MULTIPLY -> Multiply(stack[stack.size-2], stack[stack.size-1])
                    Operand.DIVIDE -> Divide(stack[stack.size-2], stack[stack.size-1])
                    Operand.SQUARE_ROOT -> SquareRoot(stack[stack.size-1])
                }

                if(item != null) {
                    for(i in 1..operator.operands)
                        stack.removeAt(stack.size-1)

                    stack.add(item)
                }
            }
        }
        else {
            if(argument.toDoubleOrNull() != null || "true" == argument || "false" == argument)
                stack.add(Literal(argument))
            else
                stack.add(Variable(argument))
        }
    }

    if(stack.size != 1)
        throw IllegalArgumentException("Invalid formula provided: $input")

    return stack[0]
}

fun ParseTreeItem.padOperand(operand: ParseTreeItem): String {
    if(this.getPrecedence() < operand.getPrecedence())
        return "(" + operand.generateString() + ")"

    return operand.generateString()
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
        is Variable -> return 0
        is Plus -> Operand.PLUS
        is Minus -> Operand.MINUS
        is Multiply -> Operand.MULTIPLY
        is Divide -> Operand.DIVIDE
        is SquareRoot -> Operand.SQUARE_ROOT
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
        is Literal -> return this.value
        is Variable -> return this.name
        is Plus -> return this.padOperand(operandA) + " + " + this.padOperand(operandB)
        is Minus -> return this.padOperand(operandA) + " - " + this.padOperand(operandB)
        is Multiply -> return this.padOperand(operandA) + " * " + this.padOperand(operandB)
        is Divide -> return this.padOperand(operandA) + " / " + this.padOperand(operandB)
        is SquareRoot -> return "sqrt(" + operandA.generateString() + ")"
    }
}
