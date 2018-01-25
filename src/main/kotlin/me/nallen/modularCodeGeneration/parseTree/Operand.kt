package me.nallen.modularCodeGeneration.parseTree

/**
 * Enum that determines whether the operator is left or right associative (or not at all)
 */
internal enum class Associativity {
    LEFT, RIGHT, NONE
}

/**
 * Enum of all the different operators that can be represented
 */
internal enum class Operand {
    AND, OR, NOT,
    GREATER_THAN_OR_EQUAL, GREATER_THAN, LESS_THAN_OR_EQUAL, LESS_THAN,
    EQUAL, NOT_EQUAL,
    OPEN_BRACKET, CLOSE_BRACKET,
    FUNCTION_CALL, FUNCTION_SEPARATOR,
    PLUS, MINUS, MULTIPLY, DIVIDE, NEGATIVE,
    SQUARE_ROOT, EXPONENTIAL
}

/**
 * An operator and its properties
 */
internal data class Operator(
        var symbol: String,
        var operands: Int,
        var associativity: Associativity,
        var precedence: Int,
        var commutative: Boolean
)

/**
 * Get the properties for a given operator enum
 */
internal fun getOperator(operand: Operand): Operator {
    // Each operator enum corresponds to a pre-defined set of properties
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

/**
 * Gets a map of all the operators and their properties
 */
internal fun getMapOfOperators(): Map<Operand, Operator> {
    // Get all instances of the enum, and then map them to a <Key, Value> pair
    return Operand.values().map { Pair(it, getOperator(it)) }.toMap()
}

/**
 * Detects what the next operator should be for the given input string.
 * If the first character(s) do not correspond to any operator then null will be returned.
 * If the start of the string matches multiple operators, the one with the longest length will be returned.
 */
internal fun getOperandForSequence(input: String): Operand? {
    // Create a list of operators that match the start of the string
    val matches = ArrayList<Operand>()
    for((operand, operator) in getMapOfOperators()) {
        // Check that the string starts with the given operator
        if(input.startsWith(operator.symbol))
            matches.add(operand)
    }

    // If we found at least one operator that matches
    if(matches.size > 0)
        // We want to return the operator with the longest symbol (sort by length, take longest)
        return matches.sortedWith(compareBy({getOperator(it).symbol.length}, {getOperator(it).operands})).last()

    // Alternatively, we can see if the start of the string is a valid custom function name
    if(getFunctionName(input) != null)
        return Operand.FUNCTION_CALL

    // Or, if we're looking at a postfix equation, it may be our custom postfix function style
    if(isPostfixedFunction(input))
        return Operand.FUNCTION_CALL

    // Otherwise, no operator found
    return null
}

/**
 * Check if the given string completely matches the custom postfix function syntax.
 * E.g. my_function<2>
 */
internal fun isPostfixedFunction(input: String): Boolean {
    val functionRegex = Regex("^(.+)<(\\d+)>$$")

    return functionRegex.matches(input)
}

/**
 * Check if the given string starts with a valid custom function name.
 * The return value is both the name of the function, and how many characters until the open parenthesis.
 * If no valid function is found, then null is returned
 */
internal fun getFunctionName(input: String): Pair<String, Int>? {
    // A function is a sequence of characters, followed by an open bracket
    val functionRegex = Regex("^([-_a-zA-Z]+)(\\s*)\\(")

    // Check if we can find a function at the start
    val match = functionRegex.find(input)
    if(match != null) {
        // Here we return both the name, and the length until the open parenthesis
        return Pair(match.groupValues[1], match.groupValues[0].length)
    }

    // No match, not a function
    return null
}

/**
 * Convert a given infix notation equation to postfix (a.k.a Reverse Polish Notation)
 */
internal fun convertToPostfix(input: String): String {
    var output = ""
    var storage = ""
    var skip = 0
    val opStack = ArrayList<Operand>()
    val openBracketStack = ArrayList<Operand>()
    val functionCalls = ArrayList<Function>()
    var followingOperand: Operand? = Operand.PLUS

    // Iterate through every character in the string
    for(i in 0 until input.length) {
        // Whenever we encounter a symbol with a length greater than one, this value will be set to skip the appropriate
        // number of characters
        if(skip > 0) {
            skip--
            continue
        }

        // Try and parse the first operand in the remaining input string
        var operand = getOperandForSequence(input.substring(i))

        // Once we find an operand (or encounter whitespace), we need to add any values that we found before this into
        // the output string first
        if((operand != null || input[i].isWhitespace()) && storage.isNotEmpty()) {
            output += storage + " "

            storage = ""
        }

        // If we were able to find an operator, then we have a lot more processing to do
        if(operand != null) {
            // Get the properties for the operator
            var operator = getOperator(operand)
            skip = 0

            // Close brackets are a special case to check for
            if(operand == Operand.CLOSE_BRACKET) {
                // We need to find the matching open bracket for precedence
                val isFunctionCall = openBracketStack.isNotEmpty() && openBracketStack.last() == Operand.FUNCTION_CALL

                // Keep iterating over the operator stack until we get to the last open bracket (could be a function
                // call)
                while(opStack.last() != Operand.OPEN_BRACKET && opStack.last() != Operand.FUNCTION_CALL) {
                    // While we keep finding operands that aren't the bracket, keep adding them to the output string
                    // One exception is that we don't add function separators (commas)
                    if(opStack.last() != Operand.FUNCTION_SEPARATOR)
                        output += getOperator(opStack.last()).symbol + " "
                    opStack.removeAt(opStack.size-1)

                    // If we reach the end of the stack, then an invalid formula has been provided because of
                    // unmatched parentheses
                    if(opStack.size == 0) {
                        throw IllegalArgumentException("Unmatched parenthesis in formula $input")
                    }
                }

                // If we get here then that means we've found the matching open bracket

                // If this was the closing bracket for a function call
                if (isFunctionCall) {
                    // We need to do some addition checks for the function name and parameters

                    if(followingOperand == Operand.FUNCTION_CALL)
                        //TODO: Why is this here?
                        functionCalls.last().parameters--
                    else if(followingOperand != null && !operandsCanExistTogether(followingOperand, operand))
                        // If the function can't follow the previous operand then there's an error
                        // E.g. following a close bracket
                        throw IllegalArgumentException("Invalid sequence of operators in formula $input")

                    // Add the function name and parameters in the format NAME<NUM_PARAMETERS> to the output string
                    output += "${functionCalls.last().name}<${functionCalls.last().parameters}> "
                    functionCalls.removeAt(functionCalls.size - 1)
                }

                // Remove the bracket from the stack
                openBracketStack.removeAt(openBracketStack.size-1)
                opStack.removeAt(opStack.size-1)

                followingOperand = operand
            }
            else {
                // This means the operator is not a close bracket
                // Now we need to check that it's not an open bracket or function call
                if(operand != Operand.OPEN_BRACKET && operand != Operand.FUNCTION_CALL) {
                    // The operator is a "normal" operator that needs precedence checks
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
                    // The operator is some form of open bracket
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

    return true
}

internal data class Function(var name: String, var parameters: Int = 1)
