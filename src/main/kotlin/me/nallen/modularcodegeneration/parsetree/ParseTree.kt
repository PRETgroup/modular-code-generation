package me.nallen.modularcodegeneration.parsetree

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import me.nallen.modularcodegeneration.codegen.c.Utils
import me.nallen.modularcodegeneration.logging.Logger

/**
 * A class that represents a single node of a Parse Tree.
 * A Parse Tree is a object based representation of a formula, each node is some form of operator / value, and the
 * children of each node are its arguments. Note that the order of these arguments may matter!
 *
 * A ParseTreeItem is the generic base object that all operations inherit from.
 * Creating an instance of this will automatically create the appropriate operation instance
 */
sealed class ParseTreeItem(var type: String) {
    companion object Factory {
        // Method for creating from a String (used in JSON parsing)
        @JsonCreator @JvmStatic
        fun generate(input: String): ParseTreeItem = generateParseTreeFromString(input)

        // Method for creating from an Int (used in JSON parsing)
        @JsonCreator @JvmStatic
        fun generate(input: Int): ParseTreeItem = generateParseTreeFromString(input.toString())

        // Method for creating from a Double (used in JSON parsing)
        @JsonCreator @JvmStatic
        fun generate(input: Double): ParseTreeItem = generateParseTreeFromString(input.toString())

        // Method for creating from a Boolean (used in JSON parsing)
        @JsonCreator @JvmStatic
        fun generate(input: Boolean): ParseTreeItem = generateParseTreeFromString(input.toString())
    }

    /**
     * Generate the infix notation equivalent of this representation
     */
    @JsonValue
    fun getString(): String {
        return this.generateString()
    }
}

// All of the operations

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

data class Sine(var operandA: ParseTreeItem): ParseTreeItem("sine")
data class Cosine(var operandA: ParseTreeItem): ParseTreeItem("cosine")
data class Tangent(var operandA: ParseTreeItem): ParseTreeItem("tangent")

class Pi(): ParseTreeItem("pi")

// Also supported are variables and literals
data class Variable(var name: String, var value: ParseTreeItem? = null): ParseTreeItem("variable")
data class Literal(var value: String): ParseTreeItem("literal")

/**
 * The supported types of variables, either Boolean (true / false), or Real (generally represented by double)
 */
enum class VariableType {
    BOOLEAN, REAL, INTEGER, ANY
}

/**
 * Generates a complete Parse Tree from a given input string (formula)
 */
fun generateParseTreeFromString(input: String): ParseTreeItem {
    // First, we want to convert the infix equation into a postfix equation
    val postfix = convertToPostfix(input)

    // In our postfix format, every argument or operator is separated by a space, so we can simply split on spaces
    val arguments = postfix.split(" ")
    val stack = ArrayList<ParseTreeItem>()

    // For every argument, we need to parse it
    for(argument in arguments) {
        if(argument.isBlank())
            continue

        // Try and see if the current argument is an operator
        val operand = getOperandForSequence(argument)

        if(operand != null) {
            // It is an operator!
            try {
                // Depending on what operator we're looking at, we want to create the correct ParseTreeItem, and add
                // whatever children (arguments) it needs. In postfix notation the arguments will always just be on the
                // top of the stack
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
                    Operand.OPEN_BRACKET -> null // We don't need to represent brackets in the Parse Tree
                    Operand.CLOSE_BRACKET -> null // We don't need to represent brackets in the Parse Tree
                    Operand.FUNCTION_CALL -> {
                        // For a Function Call, we need to extract the function name, and how many arguments it takes
                        val regex = Regex("^(.+)<(\\d+)>$")
                        val match = regex.matchEntire(argument)

                        // This should always be non-null, otherwise something weird has happened
                        if(match != null) {
                            // We want to fetch all the arguments for this function, which is represented in the angle
                            // brackets
                            val functionArguments = ArrayList<ParseTreeItem>()
                            try {
                                (match.groupValues[2].toInt() downTo 1).mapTo(functionArguments) { stack[stack.size-it] }

                                // Create the ParseTreeItem that represents a Function Call
                                FunctionCall(match.groupValues[1], functionArguments)
                            }
                            catch(ex: ArrayIndexOutOfBoundsException) {
                                // This can happen if, for example, an empty argument is given to a function
                                throw IllegalArgumentException("Unable to correctly parse function ${match.groupValues[1]} in $input")
                            }
                        }
                        else
                            // Shouldn't happen, but just to be safe there's this check
                            throw IllegalArgumentException("An error occurred while trying to parse the function $argument")
                    }
                    Operand.FUNCTION_SEPARATOR -> null // We don't need to represent function separators in the Parse Tree
                    Operand.PLUS -> Plus(stack[stack.size-2], stack[stack.size-1])
                    Operand.MINUS -> Minus(stack[stack.size-2], stack[stack.size-1])
                    Operand.NEGATIVE -> Negative(stack[stack.size-1])
                    Operand.MULTIPLY -> Multiply(stack[stack.size-2], stack[stack.size-1])
                    Operand.DIVIDE -> Divide(stack[stack.size-2], stack[stack.size-1])
                    Operand.SQUARE_ROOT -> SquareRoot(stack[stack.size-1])
                    Operand.EXPONENTIAL -> Exponential(stack[stack.size-1])
                    Operand.SCIENTIFIC_NOTATION_NEGATIVE -> Literal(String.format("%sE-%s", stack[stack.size-2].getString(), stack[stack.size-1].getString()))
                    Operand.SCIENTIFIC_NOTATION_POSITIVE -> Literal(String.format("%sE+%s", stack[stack.size-2].getString(), stack[stack.size-1].getString()))
                    Operand.SINE -> Sine(stack[stack.size-1])
                    Operand.COSINE -> Cosine(stack[stack.size-1])
                    Operand.TANGENT -> Tangent(stack[stack.size-1])
                    Operand.PI -> Pi()
                }

                // If we were able to extract an operator (i.e. it wasn't a bracket or function separator)
                if(item != null) {
                    // We need to remove however many children we just added to this operator
                    var numOperands = getOperator(operand).operands
                    if(item is FunctionCall)
                        // For Function Calls, the number of children depends on the number of arguments
                        numOperands = item.arguments.size

                    for(i in 1..numOperands)
                        stack.removeAt(stack.size-1)

                    // Now add the current item onto the stack to be used by any subsequent operations
                    stack.add(item)
                }
            }
            catch(ex: ArrayIndexOutOfBoundsException) {
                // If this exception happens, it means that there weren't enough literals or variables in the formula
                // and not every operator could be created
                throw IllegalArgumentException("Incorrect number of arguments provided to ${operand.name}: $input. ")
            }
        }
        else {
            // Not an operator, either a literal or variable, so let's test
            if(getTypeFromLiteral(argument) != null)
                // If we can successfully parse it as a literal then it must be a literal
                stack.add(Literal(argument))
            else
                // Must be a variable, so let's say so
                stack.add(Variable(argument))
        }
    }

    // We should only have one item left on the stack (the root item), if that's not the case then something bad has
    // happened
    if(stack.size != 1)
        throw IllegalArgumentException("Invalid formula provided: $input")

    // The remaining item in the stack is the root item
    return stack[0]
}

/**
 * Tries to parse the provided string for a literal and returns its type
 * If it returns null, then that means that the provided string was not a valid literal
 */
private fun getTypeFromLiteral(literal: String): VariableType? {
    // First try see if it can be parsed as a double
    if(literal.toDoubleOrNull() != null)
        return VariableType.REAL
    // Next, try for a boolean
    else if("true" == literal || "false" == literal)
        return VariableType.BOOLEAN

    // Can't parse it as a literal
    return null
}

/**
 * Looks at a provided child and generates an infix string representing it.
 * If required, this method will add brackets around any operations that require them
 */
fun ParseTreeItem.padOperand(operand: ParseTreeItem): String {
    // We need to look at the precedence of both this operator and the child to decide whether brackets are needed
    var precedence = this.getPrecedence()
    // An interesting case happens when one of the operators is not commutative and the child has 2 or more arguments
    // In this case we try to be safe by including brackets if they previously had the same precedence
    if((!operand.getCommutative() || !this.getCommutative()) && operand.getChildren().size > 1) {
        precedence--
    }
    // If the current operation's precedence is more than the childs, then we want brackets
    if(precedence < operand.getPrecedence())
        return "(" + operand.generateString() + ")"

    // Otherwise no brackets
    return operand.generateString()
}

/**
 * Returns the number that corresponds to the precedence of the ParseTreeItem.
 * A smaller number indicates higher precedence (evaluated first)
 */
fun ParseTreeItem.getPrecedence(): Int {
    // Let's get the operator that corresponds to this ParseTreeItem
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
        is Variable -> return 0 // Variables are of the highest precedence, 0
        is Literal -> return 0 // Literals are of the highest precedence, 0
        is Plus -> Operand.PLUS
        is Minus -> Operand.MINUS
        is Negative -> Operand.NEGATIVE
        is Multiply -> Operand.MULTIPLY
        is Divide -> Operand.DIVIDE
        is SquareRoot -> Operand.SQUARE_ROOT
        is Exponential -> Operand.EXPONENTIAL
        is Sine -> Operand.SINE
        is Cosine -> Operand.COSINE
        is Tangent -> Operand.TANGENT
        is Pi -> Operand.PI
    }

    // And now the precedence that corresponds to that operator
    return getOperator(operand).precedence
}

/**
 * Returns whether the ParseTreeItem is commutative (order of arguments doesn't matter)
 */
fun ParseTreeItem.getCommutative(): Boolean {
    // Let's get the operator that corresponds to this ParseTreeItem
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
        is Literal -> return false // Does not matter, Literals have no children
        is Variable -> return false // Does not matter, Variables have no children
        is Plus -> Operand.PLUS
        is Minus -> Operand.MINUS
        is Negative -> Operand.NEGATIVE
        is Multiply -> Operand.MULTIPLY
        is Divide -> Operand.DIVIDE
        is SquareRoot -> Operand.SQUARE_ROOT
        is Exponential -> Operand.EXPONENTIAL
        is Sine -> Operand.SINE
        is Cosine -> Operand.COSINE
        is Tangent -> Operand.TANGENT
        is Pi -> Operand.PI
    }

    // And now whether or not that operator is commutative
    return getOperator(operand).commutative
}

/**
 * Generates an infix formula for the ParseTreeItem.
 *
 * This will recursively go through the Parse Tree to generate the string, including adding brackets where necessary.
 */
fun ParseTreeItem.generateString(): String {
    // For each possible ParseTreeItem we have a different output string that will be generated
    // We recursively call these generation functions until we reach the end of the tree
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
            // For Function Calls, we want to generate a list of the arguments separated by commas
            val builder = StringBuilder()
            for(argument in arguments) {
                if(builder.isNotEmpty()) builder.append(", ")
                builder.append(argument.generateString())
            }

            // And now compose the function name and arguments together
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
        is Sine -> return "sin(" + operandA.generateString() + ")"
        is Cosine -> return "cos(" + operandA.generateString() + ")"
        is Tangent -> return "tan(" + operandA.generateString() + ")"
        is Pi -> return "pi"
    }
}

/**
 * Returns all the children of this ParseTreeItem
 */
fun ParseTreeItem.getChildren(): Array<ParseTreeItem> {
    // Each ParseTreeItem has a different set of children, so we go through each of them
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
        is FunctionCall -> arguments.toTypedArray() // Functions have children defined as "arguments"
        is Literal -> arrayOf() // Literals have no children
        is Variable -> if(value != null) arrayOf(value!!) else arrayOf()
        is Plus -> arrayOf(operandA, operandB)
        is Minus -> arrayOf(operandA, operandB)
        is Negative -> arrayOf(operandA)
        is Multiply -> arrayOf(operandA, operandB)
        is Divide -> arrayOf(operandA, operandB)
        is SquareRoot -> arrayOf(operandA)
        is Exponential -> arrayOf(operandA)
        is Sine -> arrayOf(operandA)
        is Cosine -> arrayOf(operandA)
        is Tangent -> arrayOf(operandA)
        is Pi -> arrayOf()
    }
}

/**
 * Returns what type the result of performing the operation this ParseTreeItem would be
 *
 * This can be useful when checking whether a formula makes sense, or for evaluating the formula.
 * A map of known variables and their types, as well as the return types of functions, is recommended if either
 * variables or function calls exist in the formula, otherwise they will both default to ANY
 */
fun ParseTreeItem.getOperationResultType(knownVariables: Map<String, VariableType> = HashMap(), knownFunctions: Map<String, VariableType?> = HashMap()): VariableType {
    // Each ParseTreeItem has a different return type, which is pretty straightforward
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
        is FunctionCall -> {
            // First we want to check if it's the inbuilt "delayed" function
            if(functionName == "delayed") {
                if(arguments.size >= 1) {
                    return arguments[0].getOperationResultType(knownVariables, knownFunctions)
                }
                return VariableType.ANY
            }

            knownFunctions[functionName] ?: VariableType.ANY // If we know it, otherwise ANY
        }
        is Plus -> VariableType.REAL
        is Minus -> VariableType.REAL
        is Multiply -> VariableType.REAL
        is Divide -> VariableType.REAL
        is Negative -> VariableType.REAL
        is SquareRoot -> VariableType.REAL
        is Exponential -> VariableType.REAL
        is Variable -> knownVariables[name] ?: VariableType.ANY // If we know it, otherwise ANY
        is Literal -> getTypeFromLiteral(value) ?: VariableType.ANY // We try to get the type from the value
        is Sine -> VariableType.REAL
        is Cosine -> VariableType.REAL
        is Tangent -> VariableType.REAL
        is Pi -> VariableType.REAL
    }
}

/**
 * Evaluates the given ParseTreeItem to return its Boolean output
 *
 * If the Parse Tree uses any variables, the value of these must be provided to the method
 */
fun ParseTreeItem.evaluateBoolean(var_map: Map<String, Literal> = HashMap()): Boolean {
    // Currently don't have support for function calls in evaluation, could be added at a later date
    if(this is FunctionCall) {
        throw IllegalArgumentException("Unable to evaluate expression involving custom function calls")
    }

    // If we're trying to evaluate a variable we need to make sure that we're provided a value for it
    if(this is Variable && !var_map.containsKey(name)) {
        throw IllegalArgumentException("Unable to evaluate expression where not all variables have values")
    }

    // Depending on what ParseTreeItem this is, we'll do a different operation. Arguments get called recursively to
    // evaluate them. Note that this is only the set of Boolean operations
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

        is Variable -> var_map.getValue(name).evaluateBoolean(var_map) // Variables that we know about
        is Literal -> when (value) { // For Literals, Boolean values represent their value
            "true" -> true
            "false" -> false
            else -> (value.toDoubleOrNull() ?: 0.0) != 0.0 // While Real values are tested whether they're 0
        }

        // Otherwise this must be an operator that actually returns a Real, so let's test whether it equals 0
        else -> this.evaluateReal(var_map) != 0.0
    }
}


/**
 * Evaluates the given ParseTreeItem to return its Real (Double) output
 *
 * If the Parse Tree uses any variables, the value of these must be provided to the method
 */
fun ParseTreeItem.evaluateReal(var_map: Map<String, Literal> = HashMap()): Double {
    // Currently don't have support for function calls in evaluation, could be added at a later date
    if(this is FunctionCall) {
        throw IllegalArgumentException("Unable to evaluate expression involving custom function calls")
    }

    // If we're trying to evaluate a variable we need to make sure that we're provided a value for it
    if(this is Variable && !var_map.containsKey(name)) {
        throw IllegalArgumentException("Unable to evaluate expression where not all variables have values")
    }

    // Depending on what ParseTreeItem this is, we'll do a different operation. Arguments get called recursively to
    // evaluate them. Note that this is only the set of Real operations
    return when(this) {
        is Plus -> operandA.evaluateReal(var_map) + operandB.evaluateReal(var_map)
        is Minus -> operandA.evaluateReal(var_map) - operandB.evaluateReal(var_map)
        is Multiply -> operandA.evaluateReal(var_map) * operandB.evaluateReal(var_map)
        is Divide -> operandA.evaluateReal(var_map) / operandB.evaluateReal(var_map)
        is Negative -> -1 * operandA.evaluateReal(var_map)
        is SquareRoot -> Math.sqrt(operandA.evaluateReal(var_map))
        is Exponential -> Math.exp(operandA.evaluateReal(var_map))
        is Sine -> Math.sin(operandA.evaluateReal(var_map))
        is Cosine -> Math.cos(operandA.evaluateReal(var_map))
        is Tangent -> Math.tan(operandA.evaluateReal(var_map))
        is Pi -> Math.PI

        is Variable -> var_map.getValue(name).evaluateReal(var_map) // Variables that we know about
        is Literal -> when (value) { // For Literals, Boolean values represent 1 or 0
            "true" -> 1.0
            "false" -> 0.0
            else -> value.toDoubleOrNull() ?: 0.0 // While Real values are just their value
        }

        // Otherwise this must be an operator that actually returns a Boolean, so make it represent 1 and 0
        else -> if(this.evaluateBoolean(var_map)) 1.0 else 0.0
    }
}

/**
 * Evaluates the given ParseTreeItem to return its value. This value may be either Boolean or Real in nature, hence why
 * it returns Any type.
 *
 * If the Parse Tree uses any variables, the value of these must be provided to the method
 */
fun ParseTreeItem.evaluate(var_map: Map<String, Literal> = HashMap()): Any {
    // Depending on if the operation returns a Boolean or Real, we want to call a different method
    return when(this.getOperationResultType()) {
        VariableType.BOOLEAN -> this.evaluateBoolean(var_map)
        VariableType.REAL -> this.evaluateReal(var_map)
        VariableType.INTEGER -> this.evaluateReal(var_map)
        VariableType.ANY -> this.evaluateReal(var_map)
    }
}

/**
 * Sets the value of a variable in the Parse Tree
 *
 * This sets the value of any variable underneath this node with name "key" to "value", no matter how many operations
 * deep
 */
fun ParseTreeItem.setParameterValue(key: String, value: ParseTreeItem): ParseTreeItem {
    val children = this.getChildren()

    // For every child we want to call this recursively
    for(child in children) {
        child.setParameterValue(key, value)
    }

    // If this is a matching variable then we want to set its value to what was provided
    if(this is Variable) {
        if(this.name == key)
            this.value = value
    }

    // Return itself to support chaining
    return this
}

/**
 * Prepends something in front of all variables
 */
fun ParseTreeItem.prependVariables(prependString: String, asVariable: Boolean = true): ParseTreeItem {
    // We only care about variables, otherwise we just leave it to be
    // We recursively call this function until we reach the end of the tree
    if(this is Variable) {
        // Let's prepend it
        if(asVariable)
            this.name = Utils.createVariableName(prependString, this.name)
        else
            this.name = prependString + this.name
    }

    for(child in this.getChildren()) {
        child.prependVariables(prependString, asVariable)
    }

    return this
}

/**
 * Replaces variable names with something else
 */
fun ParseTreeItem.replaceVariables(map: Map<String, String>): ParseTreeItem {
    // We only care about variables, otherwise we just leave it to be
    // We recursively call this function until we reach the end of the tree
    if(this is Variable) {
        // Let's check if it exists
        if(map.containsKey(this.name)) {
            // Let's replace it
            this.name = map.getValue(this.name)
        }
    }

    for(child in this.getChildren()) {
        child.replaceVariables(map)
    }

    return this
}

/**
 * Replaces variable names with something else
 */
fun ParseTreeItem.replaceVariablesWithParseTree(map: Map<String, ParseTreeItem>): ParseTreeItem {
    // We only care about variables, otherwise we just leave it to be
    // We recursively call this function until we reach the end of the tree
    if(this is Variable) {
        // Let's check if it exists
        if(map.containsKey(this.name)) {
            // Let's replace it
            this.value = map.getValue(this.name)
        }
    }

    for(child in this.getChildren()) {
        child.replaceVariablesWithParseTree(map)
    }

    return this
}

/**
 * Collects all variables used within a ParseTreeItem
 */
fun ParseTreeItem.collectVariables(): List<String> {
    val variables = ArrayList<String>()

    // If this is a variable
    if(this is Variable) {
        // Then let's add what we've found!
        variables.add(this.name)
    }
    else {
        // Otherwise we go through each child and fetch its variables
        for(child in this.getChildren()) {
            variables.addAll(child.collectVariables())
        }
    }

    // Return the list of variables we've found
    return variables
}

/**
 * Get the expected types of each child of this ParseTreeItem. If this is a function call then we use the required
 * arguments for the function to populate this list, otherwise it just depends on the operand.
 * NOTE: There's a special case for Equal and NotEqual, which can take any type but require both operands be the same
 * type
 */
fun ParseTreeItem.getExpectedTypes(functionArguments: Map<String, List<VariableType>> = mapOf()): Array<VariableType> {
    return when(this) {
        is And -> arrayOf(VariableType.BOOLEAN, VariableType.BOOLEAN)
        is Or -> arrayOf(VariableType.BOOLEAN, VariableType.BOOLEAN)
        is Not -> arrayOf(VariableType.BOOLEAN)
        is GreaterThanOrEqual -> arrayOf(VariableType.REAL, VariableType.REAL)
        is GreaterThan -> arrayOf(VariableType.REAL, VariableType.REAL)
        is LessThanOrEqual -> arrayOf(VariableType.REAL, VariableType.REAL)
        is LessThan -> arrayOf(VariableType.REAL, VariableType.REAL)
        is Equal -> arrayOf(VariableType.ANY, VariableType.ANY)
        is NotEqual -> arrayOf(VariableType.ANY, VariableType.ANY)
        is FunctionCall -> {
            // We also have a special custom function called "delayed" which takes a first parameter of any type, and
            // then a real number as the second
            if(functionName == "delayed") {
                arrayOf(VariableType.ANY, VariableType.REAL)
            }
            else if(functionArguments.containsKey(functionName)) {
                functionArguments[this.functionName]!!.toTypedArray()
            }
            else {
                arrayOf()
            }
        }
        is Plus -> arrayOf(VariableType.REAL, VariableType.REAL)
        is Minus -> arrayOf(VariableType.REAL, VariableType.REAL)
        is Multiply -> arrayOf(VariableType.REAL, VariableType.REAL)
        is Divide -> arrayOf(VariableType.REAL, VariableType.REAL)
        is Negative -> arrayOf(VariableType.REAL)
        is SquareRoot -> arrayOf(VariableType.REAL)
        is Exponential -> arrayOf(VariableType.REAL)
        is Variable -> arrayOf()
        is Literal -> arrayOf()
        is Sine -> arrayOf(VariableType.REAL)
        is Cosine -> arrayOf(VariableType.REAL)
        is Tangent -> arrayOf(VariableType.REAL)
        is Pi -> arrayOf()
    }
}

/**
 * Validates a ParseTreeItem to make sure that the operations that it is performing make sense in terms of variable
 * types, number of arguments to each operand, etc.
 */
fun ParseTreeItem.validate(variableTypes: Map<String, VariableType>, functionTypes: Map<String, VariableType?>, functionArguments: Map<String, List<VariableType>>, location: String = "'${this.generateString()}'"): Boolean {
    var valid = true

    // First we start by getting the types that we expect this operand to hold
    val expectedTypes = getExpectedTypes(functionArguments)

    // Then if this is a variable we want to check that we know about it
    if(this is Variable && !variableTypes.containsKey(name)) {
        Logger.error("Unknown variable '$name' used in $location.")
        valid = false
    }

    // Now we can start looking at the children of this item
    val children = getChildren()
    if(this is FunctionCall) {
        // For functions, there's a special "delayed" function we need to look at, in addition to any other custom
        // function
        if(functionName == "delayed" || functionArguments.containsKey(functionName)) {
            // Check that the correct number of arguments is provided, otherwise error
            if(children.size != expectedTypes.size) {
                Logger.error("Invalid number of arguments supplied to function '$functionName' in $location." +
                        " Found ${children.size}, expected ${expectedTypes.size}.")
                valid = false
            }
        }
        else {
            // If we don't know the function, then that's an error
            Logger.error("Unknown function '$functionName' used in $location.")
            valid = false
        }
    }

    // The user-friendly name that we'll print out during errors
    val name = if(this is FunctionCall) { functionName } else { type }

    // Now let's go through each expected type
    for((i, type) in expectedTypes.withIndex()) {
        if(children.size > i) {
            // And check that we provided a valid argument to it by first getting the type of the argument
            val childType = children[i].getOperationResultType(variableTypes, functionTypes)
            // And then comparing it with what we expect
            if(childType != type && type != VariableType.ANY) {
                Logger.error("Invalid argument supplied to position $i of '$name' in $location." +
                        " Found '$childType', expected '$type'.")
                valid = false
            }
        }
    }

    // Special case for Equal and Not-Equal
    if(this is Equal || this is NotEqual) {
        // Here, the two arguments must have the SAME type, so get the type for each argument
        val childType0 = children[0].getOperationResultType(variableTypes, functionTypes)
        val childType1 = children[1].getOperationResultType(variableTypes, functionTypes)

        // And then check that they're the same
        if(childType0 != childType1) {
            Logger.error("Non-matching arguments supplied to '$name' in $location." +
                    " Found '$childType0' and '$childType1', expected matching arguments.")
            valid = false
        }
    }

    // Finally, recursively validate
    for(child in children) {
        valid = valid and child.validate(variableTypes, functionTypes, functionArguments, location)
    }

    return valid
}