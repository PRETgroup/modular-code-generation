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

internal val operands: Map<Operand, Operator> = hashMapOf(
        Operand.AND to Operator("&&", 2, Associativity.LEFT, 11, true),
        Operand.OR to Operator("||", 2, Associativity.LEFT, 12, true),
        Operand.NOT to Operator("!", 1, Associativity.RIGHT, 2, true),
        Operand.GREATER_THAN_OR_EQUAL to Operator(">=", 2, Associativity.LEFT, 6, false),
        Operand.GREATER_THAN to Operator(">", 2, Associativity.LEFT, 6, false),
        Operand.LESS_THAN_OR_EQUAL to Operator("<=", 2, Associativity.LEFT, 6, false),
        Operand.LESS_THAN to Operator("<", 2, Associativity.LEFT, 6, false),
        Operand.EQUAL to Operator("==", 2, Associativity.LEFT, 7, true),
        Operand.NOT_EQUAL to Operator("!=", 2, Associativity.LEFT, 7, true),
        Operand.OPEN_BRACKET to Operator("(", 0, Associativity.NONE, 1, true),
        Operand.CLOSE_BRACKET to Operator(")", 0, Associativity.NONE, 1, true),
        Operand.FUNCTION_SEPARATOR to Operator(",", 0, Associativity.NONE, 1, true),
        Operand.PLUS to Operator("+", 2, Associativity.LEFT, 4, true),
        Operand.MINUS to Operator("-", 2, Associativity.LEFT, 4, false),
        Operand.NEGATIVE to Operator("`", 1, Associativity.RIGHT, 2, true),
        Operand.MULTIPLY to Operator("*", 2, Associativity.LEFT, 3, true),
        Operand.DIVIDE to Operator("/", 2, Associativity.LEFT, 3, false),
        Operand.SQUARE_ROOT to Operator("sqrt", 1, Associativity.RIGHT, 3, true),
        Operand.EXPONENTIAL to Operator("exp", 1, Associativity.RIGHT, 3, true)
)

internal fun getOperandForSequence(input: String): Operand? {
    val matches = ArrayList<Operand>()
    for((operand, operator) in operands) {
        if(input.startsWith(operator.symbol))
            matches.add(operand)
    }

    if(matches.size > 0)
        return matches.sortedWith(compareBy({operands[it]?.symbol?.length}, {operands[it]?.operands})).last()

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
    val op_stack = ArrayList<Operand>()
    val openBracketStack = ArrayList<Operand>()
    val functionCalls = ArrayList<Function>()
    var followingOperand = true

    for(i in 0 .. input.length-1) {
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
            var operator = operands[operand]
            skip = 0;

            if(operand == Operand.CLOSE_BRACKET) {
                val isFunctionCall = openBracketStack.isNotEmpty() && openBracketStack.last() == Operand.FUNCTION_CALL

                while(op_stack.last() != Operand.OPEN_BRACKET && op_stack.last() != Operand.FUNCTION_CALL) {
                    if(op_stack.last() != Operand.FUNCTION_SEPARATOR)
                        output += operands[op_stack.last()]?.symbol + " "
                    op_stack.removeAt(op_stack.size-1)

                    if(op_stack.size == 0) {
                        throw IllegalArgumentException("Unmatched parenthesis in formula $input")
                    }
                }

                if (isFunctionCall) {
                    if(followingOperand)
                        functionCalls.last().parameters--

                    output += "${functionCalls.last().name}<${functionCalls.last().parameters}> "
                    functionCalls.removeAt(functionCalls.size - 1)
                }

                openBracketStack.removeAt(openBracketStack.size-1)
                op_stack.removeAt(op_stack.size-1)
            }
            else {
                if(operand != Operand.OPEN_BRACKET && operand != Operand.FUNCTION_CALL) {
                    if(operand == Operand.MINUS && followingOperand) {
                        operand = Operand.NEGATIVE
                        operator = operands[Operand.NEGATIVE]
                    }

                    if(operand == Operand.FUNCTION_SEPARATOR && functionCalls.isNotEmpty())
                        functionCalls[functionCalls.size-1].parameters++

                    while((op_stack.size > 0) && (op_stack.last() != Operand.OPEN_BRACKET) && (op_stack.last() != Operand.FUNCTION_CALL)) {
                        val lastOperator = operands[op_stack.last()]
                        if(lastOperator != null && operator != null) {
                            if(lastOperator.precedence > operator.precedence)
                                break

                            if((lastOperator.precedence == operator.precedence)
                                    && operator.associativity == Associativity.RIGHT)
                                break

                            output += lastOperator.symbol + " "
                        }

                        op_stack.removeAt(op_stack.size-1)
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

                op_stack.add(operand)

                followingOperand = true
            }

            if(skip == 0)
                skip = (operator?.symbol?.length ?: 1) - 1
            else
                skip -= 1
        }
        else if(!input[i].isWhitespace()){
            followingOperand = false
            storage += input[i]
        }
    }

    if(storage.isNotEmpty()) {
        output += storage + " "
    }

    while(op_stack.size > 0) {
        val lastOperator = operands[op_stack.last()]
        if(lastOperator != null)
            output += operands[op_stack.last()]?.symbol + " "

        op_stack.removeAt(op_stack.size-1)
    }

    return output
}

internal data class Function(var name: String, var parameters: Int = 1)
