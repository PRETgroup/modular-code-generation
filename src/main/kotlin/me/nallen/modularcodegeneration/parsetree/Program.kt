package me.nallen.modularcodegeneration.parsetree

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * A representation of a "program", essentially just a sequence of lines of formulae with some extra keywords for
 * control flow, and support for variables.
 */
data class Program(
        val lines: ArrayList<ProgramLine> = ArrayList(),

        val variables: ArrayList<VariableDeclaration> = ArrayList()
) {
    companion object Factory {
        // Method for creating from a String (used in JSON parsing)
        @JsonCreator @JvmStatic
        fun generate(input: String): Program = generateProgramFromString(input)
    }

    /**
     * Generate the string notation equivalent of this program
     */
    @JsonValue
    fun getString(): String {
        return this.generateString()
    }

    /**
     * Adds a variable to the program.
     * A variable needs to have a name, type, and locality (internal or external).
     * In addition, if there is a default value desired for the variable (i.e. an initial value) then this is provided.
     */
    private fun addVariable(item: String, type: VariableType, locality: Locality = Locality.INTERNAL, default: ParseTreeItem? = null): Program {
        // Check that a variable by the same name doesn't already exist
        if(!variables.any {it.name == item}) {
            // Add the variable
            variables.add(VariableDeclaration(item, type, locality, default))

            // If a default value was provided, we want to check it for new variables too
            if(default != null)
                checkParseTreeForNewVariables(default, type)
        }
        else {
            if(variables.first { it.name == item }.type == VariableType.ANY && type != VariableType.ANY) {
                variables.first { it.name == item }.type = type
            }
        }

        // Return the program for chaining
        return this
    }

    /**
     * Traverses the entire program to find all variables contained within it. Any external variables to the program are
     * provided through the "existing" argument.
     * In addition, it is recommended to provide the return type of any functions so that variable types can be
     * accurately captured
     */
    fun collectVariables(existing: List<VariableDeclaration> = ArrayList(), knownFunctionTypes: Map<String, VariableType?> = mapOf(), knownFunctionArguments: Map<String, List<VariableType>> = mapOf()): Map<String, VariableType> {
        // Now we can add each of the external variables to the program
        for(item in existing) {
            addVariable(item.name, item.type, Locality.EXTERNAL_INPUT, item.defaultValue)
        }

        // We now need to go through each line of the program to find variables, with one key point
        // We need to parse everything at *this level* before delving into branches (i.e. a breadth-first-search) so
        // that we correctly work out at what level the variable should be created at.
        // To achieve this, we keep track of any sub-programs that we need to parse, and do them later
        for(line in lines) {
            // For each line, we need to search any logic it may contain for any new variables
            when(line) {
                is Statement -> checkParseTreeForNewVariables(line.logic, VariableType.ANY, knownFunctionArguments)
                is Assignment -> {
                    val type = line.variableValue.getOperationResultType(variables.map { Pair(it.name, it.type) }.toMap(), knownFunctionTypes)

                    addVariable(line.variableName.name, type, Locality.INTERNAL)

                    // The following line will actually add the variable to the Program
                    checkParseTreeForNewVariables(line.variableName, type, knownFunctionArguments)
                    checkParseTreeForNewVariables(line.variableValue, type, knownFunctionArguments)
                }
                is Return -> checkParseTreeForNewVariables(line.logic, VariableType.ANY, knownFunctionArguments)
                is IfStatement -> {
                    checkParseTreeForNewVariables(line.condition, VariableType.ANY, knownFunctionArguments)

                    val innerVariables = line.body.collectVariables(variables, knownFunctionTypes, knownFunctionArguments)
                    for((name, type) in innerVariables) {
                        addVariable(name, type, Locality.INTERNAL)
                    }
                }
                is ElseIfStatement -> {
                    checkParseTreeForNewVariables(line.condition, VariableType.ANY, knownFunctionArguments)

                    val innerVariables = line.body.collectVariables(variables, knownFunctionTypes, knownFunctionArguments)
                    for((name, type) in innerVariables) {
                        addVariable(name, type, Locality.INTERNAL)
                    }
                }
                is ElseStatement -> {
                    val innerVariables = line.body.collectVariables(variables, knownFunctionTypes, knownFunctionArguments)
                    for((name, type) in innerVariables) {
                        addVariable(name, type, Locality.INTERNAL)
                    }
                }
                is ForStatement -> {
                    val innerVariables = line.body.collectVariables(variables, knownFunctionTypes, knownFunctionArguments)
                    for((name, type) in innerVariables) {
                        if(name != line.variableName.name)
                            addVariable(name, type, Locality.INTERNAL)
                    }
                }
                is Break -> {}
            }
        }

        // Return the list of variables found
        return variables.map { Pair(it.name, it.type) }.toMap()
    }

    /**
     * Finds and returns what type this Program will return.
     * If this Program uses any custom functions the return values of them must be explicitly given, so that return
     * types can be generated.
     * This will be null if there is no type returned from this Program
     */
    fun getReturnType(knownFunctionTypes: Map<String, VariableType?> = LinkedHashMap()): VariableType? {
        val bodiesToParse = ArrayList<Program>()

        val variableTypeMap = LinkedHashMap<String, VariableType>()
        for(variable in variables) {
            variableTypeMap[variable.name] = variable.type
        }

        // We start with no return type, until we find one. Whenever we find one we "combine" the return types to test
        // if they are the same type or not
        var currentReturnType: VariableType? = null

        for(line in lines) {
            // The only line types that matter in this case are Return statements, where we want to check what type it
            // returns, and branching instructions that contain sub-programs - which we also want to check.
            when(line) {
                is Return -> currentReturnType = combineReturnTypes(currentReturnType, line.logic.getOperationResultType(variableTypeMap, knownFunctionTypes))
                is IfStatement -> {
                    bodiesToParse.add(line.body)
                }
                is ElseIfStatement -> {
                    bodiesToParse.add(line.body)
                }
                is ElseStatement -> bodiesToParse.add(line.body)
                is ForStatement -> bodiesToParse.add(line.body)
                is Assignment -> {}
                is Break -> {}
                is Statement -> {}
            }
        }

        // Any sub-programs we found now need to be parsed too
        for(body in bodiesToParse) {
            currentReturnType = combineReturnTypes(currentReturnType, body.getReturnType(knownFunctionTypes))
        }

        // Return the final type we found
        return currentReturnType
    }

    /**
     * Gets the total number of lines that would be taken up by this program, this assumes zero whitespace and places
     * closing brackets on their own line
     */
    fun getTotalLines(): Int {
        // Start at zero
        var numberOfLines = 0

        // And then go through each line
        for(line in lines) {
            // Adding on however many lines we've found
            numberOfLines += when(line) {
                is Statement -> 1
                is Break -> 1
                is Assignment -> 1
                is Return -> 1
                is IfStatement -> 2 + line.body.getTotalLines()
                is ElseStatement -> 2 + line.body.getTotalLines()
                is ElseIfStatement -> 2 + line.body.getTotalLines()
                is ForStatement -> 2 + line.body.getTotalLines()
            }
        }

        // Finally, return the number of lines we found
        return numberOfLines
    }

    /**
     * Combines two VariableTypes into a single VariableType that matches both of the arguments.
     * A null as input is treated as "don't care", and if the two arguments are both non-null and non-equal then an
     * "Any" type will be created.
     */
    private fun combineReturnTypes(a: VariableType?, b: VariableType?): VariableType? {
        // If a is null, we don't care, just use b
        if(a == null)
            return b

        // If b is null, we don't care, just use a
        if(b == null)
            return a

        // Otherwise both non-null, check they're equal
        if(a != b)
            // Non-equal means any
            return VariableType.ANY

        // They're both equal, so doesn't matter which we return
        return a
    }

    /**
     * Checks a Parse Tree for any new variables inside the program
     */
    private fun checkParseTreeForNewVariables(item: ParseTreeItem, currentType: VariableType, functionArguments: Map<String, List<VariableType>> = mapOf()) {
        if(item is Variable) {
            addVariable(item.name, currentType)
        }

        // Get the list of types that we expect each child to have from this ParseTree
        val expectedTypes = item.getExpectedTypes(functionArguments)

        val children = item.getChildren()

        // Equal and Not-equal are special - they work with any type but just require both sides to be the same
        if(item is Equal || item is NotEqual) {
            // So let's get both the guesses for child types
            val childType0 = children[0].getOperationResultType()
            val childType1 = children[1].getOperationResultType()

            // If one is unknown (ANY) and the other is known, then use the known one as the guess for both
            if(childType0 != VariableType.ANY && childType1 == VariableType.ANY) {
                checkParseTreeForNewVariables(children[0], childType0, functionArguments)
                checkParseTreeForNewVariables(children[1], childType0, functionArguments)
            }
            else if(childType1 != VariableType.ANY && childType0 == VariableType.ANY) {
                checkParseTreeForNewVariables(children[0], childType1, functionArguments)
                checkParseTreeForNewVariables(children[1], childType1, functionArguments)
            }
            else {
                // Otherwise we have no idea, so just use their own ones
                checkParseTreeForNewVariables(children[0], childType0, functionArguments)
                checkParseTreeForNewVariables(children[1], childType1, functionArguments)
            }
        }
        else {
            // If it's not the special case then we just iterate over every child
            for((index, child) in children.withIndex()) {
                // And if we have a guess of what this should be based off of the operand
                if(index < expectedTypes.size)
                    // Then use that as the initial guess
                    checkParseTreeForNewVariables(child, expectedTypes[index], functionArguments)
                else
                    // Otherwise we don't know (ANY)
                    checkParseTreeForNewVariables(child, VariableType.ANY, functionArguments)
            }
        }
    }
}

/**
 * The declaration of a Variable - including its name, type, locality and maybe a default value
 */
data class VariableDeclaration(
        var name: String,
        var type: VariableType,
        var locality: Locality,
        var defaultValue: ParseTreeItem? = null
)

/**
 * The locality of a Variable, can either be an Internal variable or an External variable
 */
enum class Locality {
    INTERNAL, EXTERNAL_INPUT
}

/**
 * A class that represents a single line of a Program.
 * This is how an object based representation of the Program is created, each Line can be one of a finite set of
 * operations that could be done.
 */
sealed class ProgramLine(var type: String)

// All of the line types
data class Statement(var logic: ParseTreeItem): ProgramLine("statement")
class Break(): ProgramLine("breakStatement")
data class Assignment(var variableName: Variable, var variableValue: ParseTreeItem): ProgramLine("assignment")
data class Return(var logic: ParseTreeItem): ProgramLine("return")
data class IfStatement(var condition: ParseTreeItem, var body: Program): ProgramLine("ifStatement")
data class ElseStatement(var body: Program): ProgramLine("elseStatement")
data class ElseIfStatement(var condition: ParseTreeItem, var body: Program): ProgramLine("elseIfStatement")
data class ForStatement(var variableName: Variable, var lowerBound: Int, var upperBound: Int, var body: Program): ProgramLine("forStatement")

/**
 * Generates a complete Program from a given input string
 */
fun generateProgramFromString(input: String): Program {
    val program = Program()

    // Each line of the Program should be the same as a line in the string, so we can split on newlines
    val lines = input.lines()

    // And now iterate over all the lines
    var skip = 0
    var i = 0
    for(line in lines) {
        i++

        // If we've just parsed a line that included a body, then we'll use skip to skip the next lines
        if(skip > 0) {
            skip--
            continue
        }

        // Only need to look at non-blank lines
        if(line.isNotBlank()) {
            var programLine: ProgramLine? = null

            // A regex that finds conditionals, either "if", "elseif", "else if", or "else", and loops "for"
            val conditionalRegex = Regex("^\\s*((for|if|else(\\s*)if)\\s*\\((.*)\\)|else)\\s*\\{\\s*\$")
            // A regex that splits the criteria for for loops
            val forRegex = Regex("^\\s*(.*)\\s+in\\s+(\\d*)\\s+to\\s+(\\d*)\\s*\$")
            // A regex that finds return statements
            val returnRegex = Regex("^\\s*return\\s+(.*)\\s*$")
            // A regex that finds assignments
            val assignmentRegex = Regex("^\\s*([-_a-zA-Z0-9]+)\\s*=\\s*(.*)\\s*$")

            // Check if the current line is a conditional or loop
            val match = conditionalRegex.matchEntire(line)
            if(match != null) {
                // Yes it's a conditional or loop!
                // Now we want to get the inner body of the conditional by looking for the matching close bracket
                val bodyText = getTextUntilNextMatchingCloseBracket(lines.slice(IntRange(i, lines.size-1)).joinToString("\n"))

                // We now need to know to skip the same number of lines as we just fetched
                skip = bodyText.count {it == '\n'} +1

                // And create the Program for the inner body text that we'll now use
                val body = generateProgramFromString(bodyText)

                // Now we need to figure out which exact conditional or loop it was
                if(match.groupValues[1] == "else") {
                    // Else Statement
                    programLine = ElseStatement(body)
                }
                else if(match.groupValues[2] == "for") {
                    // For Statement
                    val criteriaMatch = forRegex.matchEntire(match.groupValues[4])
                    // Check if it's valid
                    if(criteriaMatch != null) {
                        // Valid, let's add it
                        programLine = ForStatement(Variable(criteriaMatch.groupValues[1]), criteriaMatch.groupValues[2].toInt(), criteriaMatch.groupValues[3].toInt(), body)
                    }
                    else {
                        // Invalid body for for loop criteria
                        throw IllegalArgumentException("Invalid definition of for loop provided: $line")
                    }
                }
                else {
                    // Either If or ElseIf, meaning that it has a condition too
                    val condition = ParseTreeItem.generate(match.groupValues[4])
                    if(match.groupValues[2] == "if")
                        // If Statement
                        programLine = IfStatement(condition, body)
                    else if(match.groupValues[2].startsWith("else"))
                        // ElseIf Statement
                        programLine = ElseIfStatement(condition, body)
                }
            }
            else {
                // Not a conditional, check if it's a return statement
                val returnMatch = returnRegex.matchEntire(line)
                programLine = if(returnMatch != null) {
                    // Yes it's a return, create the Return Line
                    Return(ParseTreeItem.generate(returnMatch.groupValues[1]))
                } else {
                    // Not a return statement either, check if it's an assignment
                    val assignmentMatch = assignmentRegex.matchEntire(line)
                    if(assignmentMatch != null) {
                        // Yes it's an assignment, create the Assignment Line
                        Assignment(Variable(assignmentMatch.groupValues[1]), ParseTreeItem.generate(assignmentMatch.groupValues[2]))
                    } else {
                        // Not an assignment either, so check if it's a break
                        if(line.trim() == "break") {
                            Break()
                        }
                        else {
                            // Not a break, so must just be a Statement Line
                            Statement(ParseTreeItem.generate(line))
                        }
                    }
                }
            }

            // If we managed to find a line we want to add it
            if(programLine != null)
                program.lines.add(programLine)
        }
    }

    // Return the program for chaining
    return program
}

/**
 * Searches the string for the next matching close bracket, and returns it
 */
fun getTextUntilNextMatchingCloseBracket(input: String): String {
    // We start off wanting to find one close bracket
    var bracketsToFind = 1
    // Iterate through the string
    for(i in 0 until input.length) {
        // Finding an open bracket increments the counter
        if(input[i] == '{')
            bracketsToFind++
        // Finding a close bracket decrements the counter
        else if(input[i] == '}')
            bracketsToFind--

        // If the counter reaches 0 then we've found our matching close bracket!
        if(bracketsToFind == 0)
            // Return a substring until this point
            return input.substring(0, i)
    }

    // Uh oh, this means no matching close bracket was found
    throw IllegalArgumentException("Invalid program!")
}

/**
 * Generates a string version of the Program that can be output. Any formulae will be output in infix notation.
 *
 * This will recursively go through the Program whenever a condition with a sub-Program is found
 */
fun Program.generateString(): String {
    val builder = StringBuilder()

    // Iterate through every line
    lines
            .map {
                // Depending on the type, the generated output will be slightly different
                when(it) {
                    is Statement -> it.logic.generateString()
                    is Break -> "break"
                    is Assignment -> "${it.variableName.generateString()} = ${it.variableValue.generateString()}"
                    is Return -> "return ${it.logic.generateString()}"
                    // The other ones (conditionals) all require indenting for their bodies too
                    is IfStatement -> "if(${it.condition.generateString()}) {\n${it.body.generateString().prependIndent("  ")}\n}"
                    is ElseIfStatement -> "else if(${it.condition.generateString()}) {\n${it.body.generateString().prependIndent("  ")}\n}"
                    is ElseStatement -> "else {\n${it.body.generateString().prependIndent("  ")}\n}"
                    is ForStatement -> "for(${it.variableName.generateString()} in ${it.lowerBound} to ${it.upperBound}){\n${it.body.generateString().prependIndent("  ")}\n}"
                }
            }
            // And append them all together!
            .forEach { builder.appendLine(it) }

    // And return the generated string
    return builder.toString().trimEnd()
}

/**
 * Sets the value of a variable in the Program
 *
 * This sets the value of any variable in this Program with name "key" to "value", no matter how many operations
 * deep
 */
fun Program.setParameterValue(key: String, value: ParseTreeItem): Program {
    // We need to go through any existing default values for variables to see if it exists in there
    for(variable in variables) {
        variable.defaultValue?.setParameterValue(key, value)
    }

    // And then go through each line
    for(line in lines) {
        // For each line we need to call the method that sets the parameter value for any ParseTreeItem we find
        // We also have to recursively call this method for any bodies we find under any conditionals
        when(line) {
            is Statement -> line.logic.setParameterValue(key, value)
            is Assignment -> {
                line.variableName.setParameterValue(key, value)
                line.variableValue.setParameterValue(key, value)
            }
            is Return -> line.logic.setParameterValue(key, value)
            is IfStatement -> {
                line.condition.setParameterValue(key, value)
                line.body.setParameterValue(key, value)
            }
            is ElseIfStatement -> {
                line.condition.setParameterValue(key, value)
                line.body.setParameterValue(key, value)
            }
            is ElseStatement -> line.body.setParameterValue(key, value)
            is ForStatement -> line.body.setParameterValue(key, value)
            is Break -> {}
        }
    }

    // Return the program for chaining
    return this
}

/**
 * Removes arguments from function calls
 */
fun Program.removeFunctionArguments(list: List<String>): Program {
    // We need to go through any existing default values for variables to see if it exists in there
    for(variable in variables) {
        variable.defaultValue?.removeFunctionArguments(list)
    }

    this.variables.filter { list.contains(it.name) }.forEach { it.locality = Locality.EXTERNAL_INPUT }

    //Let's go through each line
    for(line in lines) {
        // For each line we need to call the method that sets the parameter value for any ParseTreeItem we find
        // We also have to recursively call this method for any bodies we find under any conditionals
        when(line) {
            is Statement -> line.logic.removeFunctionArguments(list)
            is Assignment -> {
                line.variableName.removeFunctionArguments(list)
                line.variableValue.removeFunctionArguments(list)
            }
            is Return -> line.logic.removeFunctionArguments(list)
            is IfStatement -> {
                line.condition.removeFunctionArguments(list)
                line.body.removeFunctionArguments(list)
            }
            is ElseIfStatement -> {
                line.condition.removeFunctionArguments(list)
                line.body.removeFunctionArguments(list)
            }
            is ElseStatement -> line.body.removeFunctionArguments(list)
            is ForStatement -> line.body.removeFunctionArguments(list)
            is Break -> {}
        }
    }

    return this
}