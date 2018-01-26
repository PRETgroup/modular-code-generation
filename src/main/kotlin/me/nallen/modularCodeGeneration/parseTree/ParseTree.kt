package me.nallen.modularCodeGeneration.parseTree

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

sealed class ParseTreeItem(var type: String) {
    companion object Factory {
        @JsonCreator @JvmStatic
        fun generate(input: String): ParseTreeItem = generateParseTreeFromString(input)

        @JsonCreator @JvmStatic
        fun generate(input: Int): ParseTreeItem = generateParseTreeFromString(input.toString())

        @JsonCreator @JvmStatic
        fun generate(input: Double): ParseTreeItem = generateParseTreeFromString(input.toString())

        @JsonCreator @JvmStatic
        fun generate(input: Boolean): ParseTreeItem = generateParseTreeFromString(input.toString())
    }

    @JsonValue
    fun getString(): String {
        return this.generateString()
    }
}

data class And(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem("and")
data class Or(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem("or")
data class Not(var operandA: ParseTreeItem): ParseTreeItem("not")

data class GreaterThanOrEqual(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem("greaterThanOrEqualTo")
data class GreaterThan(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem("greaterThan")
data class LessThanOrEqual(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem("lessThanOrEqualTo")
data class LessThan(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem("lessThan")
data class Equal(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem("equal")
data class NotEqual(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem("notEqual")

data class FunctionCall(var functionName: String, var arguments: List<ParseTreeItem>): ParseTreeItem("functionCall")

data class Plus(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem("plus")
data class Minus(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem("minus")
data class Multiply(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem("multiply")
data class Divide(var operandA: ParseTreeItem, var operandB: ParseTreeItem): ParseTreeItem("divide")
data class Negative(var operandA: ParseTreeItem): ParseTreeItem("negative")

data class SquareRoot(var operandA: ParseTreeItem): ParseTreeItem("squareRoot")
data class Exponential(var operandA: ParseTreeItem): ParseTreeItem("exponential")

data class Variable(var name: String, var value: ParseTreeItem? = null): ParseTreeItem("variable")
data class Literal(var value: String): ParseTreeItem("literal")

enum class VariableType {
    BOOLEAN, REAL
}

fun generateParseTreeFromString(input: String): ParseTreeItem {
    val postfix = convertToPostfix(input)

    val arguments = postfix.split(" ")
    val stack = ArrayList<ParseTreeItem>()

    for(argument in arguments) {
        if(argument.isBlank())
            continue

        val operand = getOperandForSequence(argument)

        if(operand != null) {
            try {
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
                    Operand.FUNCTION_CALL -> {
                        val regex = Regex("^(.+)<(\\d+)>$")
                        val match = regex.matchEntire(argument)

                        if(match != null) {
                            val functionArguments = ArrayList<ParseTreeItem>()
                            try {
                                (match.groupValues[2].toInt() downTo 1).mapTo(functionArguments) { stack[stack.size- it] }

                                FunctionCall(match.groupValues[1], functionArguments)
                            }
                            catch(ex: ArrayIndexOutOfBoundsException) {
                                throw IllegalArgumentException("Unable to correctly parse function ${match.groupValues[1]} in $input")
                            }
                        }
                        else
                            throw IllegalArgumentException("An error occurred while trying to parse the function $argument")
                    }
                    Operand.FUNCTION_SEPARATOR -> null
                    Operand.PLUS -> Plus(stack[stack.size-2], stack[stack.size-1])
                    Operand.MINUS -> Minus(stack[stack.size-2], stack[stack.size-1])
                    Operand.NEGATIVE -> Negative(stack[stack.size-1])
                    Operand.MULTIPLY -> Multiply(stack[stack.size-2], stack[stack.size-1])
                    Operand.DIVIDE -> Divide(stack[stack.size-2], stack[stack.size-1])
                    Operand.SQUARE_ROOT -> SquareRoot(stack[stack.size-1])
                    Operand.EXPONENTIAL -> Exponential(stack[stack.size-1])
                }

                if(item != null) {
                    var numOperands = getOperator(operand).operands
                    if(item is FunctionCall)
                        numOperands = item.arguments.size

                    for(i in 1..numOperands)
                        stack.removeAt(stack.size-1)

                    stack.add(item)
                }
            }
            catch(ex: ArrayIndexOutOfBoundsException) {
                throw IllegalArgumentException("Incorrect number of arguments provided to ${operand.name}: $input. ")
            }
        }
        else {
            if(getTypeFromLiteral(argument) != null)
                stack.add(Literal(argument))
            else
                stack.add(Variable(argument))
        }
    }

    if(stack.size != 1)
        throw IllegalArgumentException("Invalid formula provided: $input")

    return stack[0]
}

private fun getTypeFromLiteral(literal: String): VariableType? {
    if(literal.toDoubleOrNull() != null)
        return VariableType.REAL
    else if("true" == literal || "false" == literal)
        return VariableType.BOOLEAN

    return null
}

fun ParseTreeItem.padOperand(operand: ParseTreeItem): String {
    var precedence = this.getPrecedence()
    if((!operand.getCommutative() || !this.getCommutative()) && operand.getChildren().size > 1) {
        precedence--
    }
    if(precedence < operand.getPrecedence())
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
        is FunctionCall -> Operand.FUNCTION_CALL
        is Plus -> Operand.PLUS
        is Minus -> Operand.MINUS
        is Negative -> Operand.NEGATIVE
        is Multiply -> Operand.MULTIPLY
        is Divide -> Operand.DIVIDE
        is SquareRoot -> Operand.SQUARE_ROOT
        is Exponential -> Operand.EXPONENTIAL
    }

    return getOperator(operand).precedence
}

fun ParseTreeItem.getCommutative(): Boolean {
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
        is FunctionCall -> Operand.FUNCTION_CALL
        is Literal -> return false
        is Variable -> return false
        is Plus -> Operand.PLUS
        is Minus -> Operand.MINUS
        is Negative -> Operand.NEGATIVE
        is Multiply -> Operand.MULTIPLY
        is Divide -> Operand.DIVIDE
        is SquareRoot -> Operand.SQUARE_ROOT
        is Exponential -> Operand.EXPONENTIAL
    }

    return getOperator(operand).commutative
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
        is FunctionCall -> {
            val builder = StringBuilder()
            for(argument in arguments) {
                if(builder.isNotEmpty()) builder.append(", ")
                builder.append(argument.generateString())
            }

            return "$functionName($builder)"
        }
        is Literal -> return this.value
        is Variable -> return this.name
        is Plus -> return this.padOperand(operandA) + " + " + this.padOperand(operandB)
        is Minus -> return this.padOperand(operandA) + " - " + this.padOperand(operandB)
        is Negative -> return "-" + this.padOperand(operandA)
        is Multiply -> return this.padOperand(operandA) + " * " + this.padOperand(operandB)
        is Divide -> return this.padOperand(operandA) + " / " + this.padOperand(operandB)
        is SquareRoot -> return "sqrt(" + operandA.generateString() + ")"
        is Exponential -> return "exp(" + operandA.generateString() + ")"
    }
}

fun ParseTreeItem.getChildren(): Array<ParseTreeItem> {
    return when (this) {
        is And -> arrayOf(operandA, operandB)
        is Or -> arrayOf(operandA, operandB)
        is Not -> arrayOf(operandA)
        is GreaterThan -> arrayOf(operandA, operandB)
        is GreaterThanOrEqual -> arrayOf(operandA, operandB)
        is LessThanOrEqual -> arrayOf(operandA, operandB)
        is LessThan -> arrayOf(operandA, operandB)
        is Equal -> arrayOf(operandA, operandB)
        is NotEqual -> arrayOf(operandA, operandB)
        is FunctionCall -> arguments.toTypedArray()
        is Literal -> arrayOf()
        is Variable -> if(value != null) arrayOf(value!!) else arrayOf()
        is Plus -> arrayOf(operandA, operandB)
        is Minus -> arrayOf(operandA, operandB)
        is Negative -> arrayOf(operandA)
        is Multiply -> arrayOf(operandA, operandB)
        is Divide -> arrayOf(operandA, operandB)
        is SquareRoot -> arrayOf(operandA)
        is Exponential -> arrayOf(operandA)
    }
}

fun ParseTreeItem.getOperationResultType(knownVariables: Map<String, VariableType> = HashMap(), knownFunctions: Map<String, VariableType?> = HashMap()): VariableType {
    return when(this) {
        is And -> VariableType.BOOLEAN
        is Or -> VariableType.BOOLEAN
        is Not -> VariableType.BOOLEAN
        is GreaterThanOrEqual -> VariableType.BOOLEAN
        is GreaterThan -> VariableType.BOOLEAN
        is LessThanOrEqual -> VariableType.BOOLEAN
        is LessThan -> VariableType.BOOLEAN
        is Equal -> VariableType.BOOLEAN
        is NotEqual -> VariableType.BOOLEAN
        is FunctionCall -> knownFunctions[functionName] ?: VariableType.REAL
        is Plus -> VariableType.REAL
        is Minus -> VariableType.REAL
        is Multiply -> VariableType.REAL
        is Divide -> VariableType.REAL
        is Negative -> VariableType.REAL
        is SquareRoot -> VariableType.REAL
        is Exponential -> VariableType.REAL
        is Variable -> knownVariables[name] ?: VariableType.REAL
        is Literal -> getTypeFromLiteral(value) ?: VariableType.REAL
    }
}

fun ParseTreeItem.evaluateBoolean(var_map: Map<String, Literal> = HashMap()): Boolean {
    if(this is FunctionCall) {
        throw IllegalArgumentException("Unable to evaluate expression involving custom function calls")
    }

    if(this is Variable && !var_map.containsKey(name)) {
        throw IllegalArgumentException("Unable to evaluate expression where not all variables have values")
    }

    return when(this) {
        is And -> operandA.evaluateBoolean(var_map) && operandB.evaluateBoolean(var_map)
        is Or -> operandA.evaluateBoolean(var_map) || operandB.evaluateBoolean(var_map)
        is Not -> !operandA.evaluateBoolean(var_map)
        is GreaterThanOrEqual -> operandA.evaluateReal(var_map) >= operandB.evaluateReal(var_map)
        is GreaterThan -> operandA.evaluateReal(var_map) > operandB.evaluateReal(var_map)
        is LessThanOrEqual -> operandA.evaluateReal(var_map) <= operandB.evaluateReal(var_map)
        is LessThan -> operandA.evaluateReal(var_map) < operandB.evaluateReal(var_map)
        is Equal -> operandA.evaluateReal(var_map) == operandB.evaluateReal(var_map)
        is NotEqual -> operandA.evaluateReal(var_map) != operandB.evaluateReal(var_map)

        is Variable -> var_map[name]!!.evaluateBoolean(var_map)
        is Literal -> when (value) {
            "true" -> true
            "false" -> false
            else -> (value.toDoubleOrNull() ?: 0.0) != 0.0
        }

        else -> this.evaluateReal(var_map) != 0.0
    }
}

fun ParseTreeItem.evaluateReal(var_map: Map<String, Literal> = HashMap()): Double {
    if(this is FunctionCall) {
        throw IllegalArgumentException("Unable to evaluate expression involving custom function calls")
    }

    if(this is Variable && !var_map.containsKey(name)) {
        throw IllegalArgumentException("Unable to evaluate expression where not all variables have values")
    }

    return when(this) {
        is Plus -> operandA.evaluateReal(var_map) + operandB.evaluateReal(var_map)
        is Minus -> operandA.evaluateReal(var_map) - operandB.evaluateReal(var_map)
        is Multiply -> operandA.evaluateReal(var_map) * operandB.evaluateReal(var_map)
        is Divide -> operandA.evaluateReal(var_map) / operandB.evaluateReal(var_map)
        is Negative -> -1 * operandA.evaluateReal(var_map)
        is SquareRoot -> Math.sqrt(operandA.evaluateReal(var_map))
        is Exponential -> Math.exp(operandA.evaluateReal(var_map))

        is Variable -> var_map[name]!!.evaluateReal(var_map)
        is Literal -> when (value) {
            "true" -> 1.0
            "false" -> 0.0
            else -> value.toDoubleOrNull() ?: 0.0
        }

        else -> if(this.evaluateBoolean(var_map)) 1.0 else 0.0
    }
}

fun ParseTreeItem.evaluate(var_map: Map<String, Literal> = HashMap()): Any {
    return when(this.getOperationResultType()) {
        VariableType.BOOLEAN -> this.evaluateBoolean(var_map)
        VariableType.REAL -> this.evaluateReal(var_map)
    }
}

fun ParseTreeItem.setParameterValue(key: String, value: ParseTreeItem): ParseTreeItem {
    val children = this.getChildren()

    for(child in children) {
        child.setParameterValue(key, value)
    }

    if(this is Variable) {
        if(this.name == key)
            this.value = value
    }

    return this
}
