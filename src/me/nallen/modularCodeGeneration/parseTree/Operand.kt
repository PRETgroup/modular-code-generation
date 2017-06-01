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
    PLUS
}

internal data class Operator(
        var symbol: String,
        var operands: Int,
        var associativity: Associativity,
        var precedence: Int
)

internal val operands: Map<Operand, Operator> = hashMapOf(
        Operand.AND to Operator("&&", 2, Associativity.LEFT, 11),
        Operand.OR to Operator("||", 2, Associativity.LEFT, 12),
        Operand.NOT to Operator("!", 1, Associativity.RIGHT, 2),
        Operand.GREATER_THAN_OR_EQUAL to Operator(">=", 2, Associativity.LEFT, 6),
        Operand.GREATER_THAN to Operator(">", 2, Associativity.LEFT, 6),
        Operand.LESS_THAN_OR_EQUAL to Operator("<=", 2, Associativity.LEFT, 6),
        Operand.LESS_THAN to Operator("<", 2, Associativity.LEFT, 6),
        Operand.EQUAL to Operator("==", 2, Associativity.LEFT, 7),
        Operand.NOT_EQUAL to Operator("!=", 2, Associativity.LEFT, 7),
        Operand.OPEN_BRACKET to Operator("(", 0, Associativity.NONE, 1),
        Operand.CLOSE_BRACKET to Operator(")", 0, Associativity.NONE, 1),
        Operand.PLUS to Operator("+", 2, Associativity.LEFT, 2)
)

internal fun getOperandForSequence(input: String): Operand? {
    for((operand, operator) in operands) {
        if(input.startsWith(operator.symbol))
            return operand
    }

    return null
}

internal fun convertToPostfix(input: String): String {
    var output = ""
    var storage = ""
    var skip = 0
    val op_stack = ArrayList<Operand>()

    for(i in 0 .. input.length-1) {
        if(skip > 0) {
            skip--
            continue
        }

        val operand = getOperandForSequence(input.substring(i))

        if((operand != null || input[i].isWhitespace()) && storage.isNotEmpty()) {
            output += storage + " "

            storage = ""
        }

        if(operand != null) {
            val operator = operands[operand]

            if(operand == Operand.CLOSE_BRACKET) {
                while(op_stack.last() != Operand.OPEN_BRACKET) {
                    output += operands[op_stack.last()]?.symbol + " "
                    op_stack.removeAt(op_stack.size-1)

                    if(op_stack.size == 0) {
                        throw IllegalArgumentException("Unmatched parenthesis in formula $input")
                    }
                }
                op_stack.removeAt(op_stack.size-1)
            }
            else {
                if(operand != Operand.OPEN_BRACKET) {
                    while((op_stack.size > 0) && (op_stack.last() != Operand.OPEN_BRACKET)) {
                        val lastOperator = operands[op_stack.last()]
                        if(lastOperator != null && operator != null) {
                            if(lastOperator.precedence > operator.precedence)
                                break

                            if((lastOperator.precedence == operator.precedence)
                                    && lastOperator.associativity != Associativity.RIGHT)
                                break

                            output += lastOperator.symbol + " "
                        }

                        op_stack.removeAt(op_stack.size-1)
                    }
                }

                op_stack.add(operand)
            }

            skip = (operator?.symbol?.length ?: 1) - 1
        }
        else if(!input[i].isWhitespace()){
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
