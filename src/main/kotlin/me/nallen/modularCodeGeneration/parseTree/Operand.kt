package me.nallen.modularCodeGeneration.parseTree

/**
 * Created by nall426 on 1/06/2017.
 */

internal enum class Associativity {
    LEFT, RIGHT, NONE
}

internal enum class Operand {
    AND, OR, NOT,
    GREATER_THAN_OR_EQUAL, GREATER_THAN, LESS_THAN_OR_EQUAL, LESS_THAN,
    EQUAL, NOT_EQUAL,
    OPEN_BRACKET, CLOSE_BRACKET,
    FUNCTION_CALL, FUNCTION_SEPARATOR,
    PLUS, MINUS, MULTIPLY, DIVIDE, NEGATIVE,
    SQUARE_ROOT, EXPONENTIAL
}

internal data class Operator(
        var symbol: String,
        var operands: Int,
        var associativity: Associativity,
        var precedence: Int,
        var commutative: Boolean
)

internal fun getOperator(operand: Operand): Operator {
    return when(operand) {
        Operand.AND -> Operator("&&", 2, Associativity.LEFT, 11, true)
        Operand.OR -> Operator("||", 2, Associativity.LEFT, 12, true)
        Operand.NOT -> Operator("!", 1, Associativity.RIGHT, 2, true)
        Operand.GREATER_THAN_OR_EQUAL -> Operator(">=", 2, Associativity.LEFT, 6, false)
        Operand.GREATER_THAN -> Operator(">", 2, Associativity.LEFT, 6, false)
        Operand.LESS_THAN_OR_EQUAL -> Operator("<=", 2, Associativity.LEFT, 6, false)
        Operand.LESS_THAN -> Operator("<", 2, Associativity.LEFT, 6, false)
        Operand.EQUAL -> Operator("==", 2, Associativity.LEFT, 7, true)
        Operand.NOT_EQUAL -> Operator("!=", 2, Associativity.LEFT, 7, true)
        Operand.OPEN_BRACKET -> Operator("(", 0, Associativity.RIGHT, 1, true)
        Operand.CLOSE_BRACKET -> Operator(")", 0, Associativity.LEFT, 1, true)
        Operand.FUNCTION_SEPARATOR -> Operator(",", 0, Associativity.NONE, 1, true)
        Operand.PLUS -> Operator("+", 2, Associativity.LEFT, 4, true)
        Operand.MINUS -> Operator("-", 2, Associativity.LEFT, 4, false)
        Operand.NEGATIVE -> Operator("`", 1, Associativity.RIGHT, 2, true)
        Operand.MULTIPLY -> Operator("*", 2, Associativity.LEFT, 3, true)
        Operand.DIVIDE -> Operator("/", 2, Associativity.LEFT, 3, false)
        Operand.SQUARE_ROOT -> Operator("sqrt", 1, Associativity.RIGHT, 3, true)
        Operand.EXPONENTIAL -> Operator("exp", 1, Associativity.RIGHT, 3, true)
        Operand.FUNCTION_CALL -> Operator("function<>(", 0, Associativity.RIGHT, 1, true)
    }
}

internal fun getMapOfOperators(): Map<Operand, Operator> {
    return Operand.values().map { Pair(it, getOperator(it)) }.toMap()
}

internal fun getOperandForSequence(input: String): Operand? {
    val matches = ArrayList<Operand>()
    for((operand, operator) in getMapOfOperators()) {
        if(input.startsWith(operator.symbol))
            matches.add(operand)
    }

    if(matches.size > 0)
        return matches.sortedWith(compareBy({getOperator(it).symbol.length}, {getOperator(it).operands})).last()

    if(getFunctionName(input) != null)
        return Operand.FUNCTION_CALL

    if(isPostfixedFunction(input))
        return Operand.FUNCTION_CALL

    return null
}

internal fun isPostfixedFunction(input: String): Boolean {
    val functionRegex = Regex("^(.+)<(\\d+)>$$")

    return functionRegex.matches(input)
}

internal fun getFunctionName(input: String): Pair<String, Int>? {
    val functionRegex = Regex("^([-_a-zA-Z]+)(\\s*)\\(")

    val match = functionRegex.find(input)
    if(match != null) {
        return Pair(match.groupValues[1], match.groupValues[0].length)
    }

    return null
}

internal fun convertToPostfix(input: String): String {
    var output = ""
    var storage = ""
    var skip = 0
    val opStack = ArrayList<Operand>()
    val openBracketStack = ArrayList<Operand>()
    val functionCalls = ArrayList<Function>()
    var followingOperand: Operand? = Operand.PLUS

    for(i in 0 until input.length) {
        if(skip > 0) {
            skip--
            continue
        }

        var operand = getOperandForSequence(input.substring(i))

        if((operand != null || input[i].isWhitespace()) && storage.isNotEmpty()) {
            output += storage + " "

            storage = ""
        }

        if(operand != null) {
            var operator = getOperator(operand)
            skip = 0

            if(operand == Operand.CLOSE_BRACKET) {
                val isFunctionCall = openBracketStack.isNotEmpty() && openBracketStack.last() == Operand.FUNCTION_CALL

                while(opStack.last() != Operand.OPEN_BRACKET && opStack.last() != Operand.FUNCTION_CALL) {
                    if(opStack.last() != Operand.FUNCTION_SEPARATOR)
                        output += getOperator(opStack.last()).symbol + " "
                    opStack.removeAt(opStack.size-1)

                    if(opStack.size == 0) {
                        throw IllegalArgumentException("Unmatched parenthesis in formula $input")
                    }
                }

                if (isFunctionCall) {
                    if(followingOperand == Operand.FUNCTION_CALL)
                        functionCalls.last().parameters--
                    else if(followingOperand != null && !operandsCanExistTogether(followingOperand, operand)) {
                        throw IllegalArgumentException("Invalid sequence of operators in formula $input")
                    }

                    output += "${functionCalls.last().name}<${functionCalls.last().parameters}> "
                    functionCalls.removeAt(functionCalls.size - 1)
                }

                openBracketStack.removeAt(openBracketStack.size-1)
                opStack.removeAt(opStack.size-1)

                followingOperand = operand
            }
            else {
                if(operand != Operand.OPEN_BRACKET && operand != Operand.FUNCTION_CALL) {
                    if(operand == Operand.MINUS && followingOperand != null && !operandsCanExistTogether(followingOperand, operand)) {
                        operand = Operand.NEGATIVE
                        operator = getOperator(Operand.NEGATIVE)
                    }
                    else if(followingOperand != null && !operandsCanExistTogether(followingOperand, operand)) {
                        throw IllegalArgumentException("Invalid sequence of operators in formula $input")
                    }

                    if(operand == Operand.FUNCTION_SEPARATOR && functionCalls.isNotEmpty())
                        functionCalls[functionCalls.size-1].parameters++

                    while((opStack.size > 0) && (opStack.last() != Operand.OPEN_BRACKET) && (opStack.last() != Operand.FUNCTION_CALL)) {
                        val lastOperator = getOperator(opStack.last())
                        if(lastOperator.precedence > operator.precedence)
                            break

                        if((lastOperator.precedence == operator.precedence)
                                && operator.associativity == Associativity.RIGHT)
                            break

                        output += lastOperator.symbol + " "

                        opStack.removeAt(opStack.size-1)
                    }
                }
                else {
                    if(operand == Operand.FUNCTION_CALL) {
                        val functionData = getFunctionName(input.substring(i))
                        if(functionData != null) {
                            val (name, skipAmount) = functionData
                            skip = skipAmount

                            functionCalls.add(Function(name))
                        }
                        else {
                            throw IllegalArgumentException("An unexpected error occured while trying to parse the formula $input")
                        }
                    }

                    openBracketStack.add(operand)
                }

                opStack.add(operand)

                followingOperand = operand
            }

            if(skip == 0)
                skip = operator.symbol.length - 1
            else
                skip -= 1
        }
        else if(!input[i].isWhitespace()){
            followingOperand = null
            storage += input[i]
        }
    }

    if(storage.isNotEmpty()) {
        output += storage + " "
    }

    while(opStack.size > 0) {
        if(opStack.last() == Operand.OPEN_BRACKET || opStack.last() == Operand.FUNCTION_CALL) {
            throw IllegalArgumentException("Unmatched parenthesis in formula $input")
        }

        val lastOperator = getOperator(opStack.last())
        output += lastOperator.symbol + " "

        opStack.removeAt(opStack.size-1)
    }

    return output
}

internal fun operandsCanExistTogether(first: Operand, second: Operand): Boolean {
    val firstOperator = getOperator(first)
    val secondOperator = getOperator(second)

    if ((firstOperator.associativity == Associativity.RIGHT && firstOperator.operands > 0)
            || (firstOperator.associativity == Associativity.LEFT && firstOperator.operands > 1)) {
        if (secondOperator.associativity == Associativity.RIGHT)
            return true

        return false
    }

    if (secondOperator.associativity == Associativity.LEFT && secondOperator.operands > 0) {
        if (firstOperator.associativity == Associativity.LEFT)
            return true

        return false
    }

    return true;
}

internal data class Function(var name: String, var parameters: Int = 1)
