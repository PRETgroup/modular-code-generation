package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.hybridAutomata.HybridAutomata
import me.nallen.modularCodeGeneration.hybridAutomata.Locality
import me.nallen.modularCodeGeneration.hybridAutomata.Variable
import me.nallen.modularCodeGeneration.parseTree.*
import me.nallen.modularCodeGeneration.utils.NamingConvention
import me.nallen.modularCodeGeneration.utils.convertWordDelimiterConvention

typealias ParseTreeLocality = me.nallen.modularCodeGeneration.parseTree.Locality

/**
 * A set of utilities for C Code generation regarding types, ParseTree generation, and naming conventions
 */
object Utils {
    // If something uses STEP_SIZE, we don't want to throw an error when there's no default value for it, because it's
    // defined in another file
    val DEFAULT_CUSTOM_VARIABLES = mapOf("STEP_SIZE" to "STEP_SIZE")

    /**
     * Convert our VariableType to a C type
     */
    fun generateCType(type: VariableType?): String {
        // Switch on the type, and return the appropriate C type
        return when(type) {
            null -> "void"
            VariableType.BOOLEAN -> "bool"
            VariableType.REAL -> "double"
        }
    }

    /**
     * Convert our VariableType to the printf specifier used for logging a variable of that type
     */
    fun generatePrintfType(type: VariableType): String {
        // Switch on the type, and return the appropriate printf specifier
        return when(type) {
            VariableType.BOOLEAN -> "%d"
            VariableType.REAL -> "%f"
        }
    }

    /**
     * Generates a string that does some arbitrary function on all variables of a given locality inside an automata.
     * The arbitrary function must take in a single Variable, and return a String. In addition, a comment can be added
     * at the start of the block of variables
     */
    fun performVariableFunctionForLocality(automata: HybridAutomata, locality: Locality, function: (v: Variable) -> String, config: Configuration = Configuration(), comment: String? = null, depth: Int = 1): String {
        val result = StringBuilder()

        // We only need to do anything if there are any variables of the requested type
        if(automata.variables.any{it.locality == locality}) {
            result.appendln()
            // If we need to generate a comment
            if(comment != null)
                // Generate the comment, at the correct indent, with the locality named after
                result.appendln("${config.getIndent(depth)}// $comment ${locality.getTextualName()}")

            // Now for each variable of the right locality, we want to add the output of the arbitrary function with the
            // variable as input
            automata.variables
                    .filter{it.locality == locality}
                    .sortedBy { it.type }
                    .map { function(it) }
                    .filter { it.isNotEmpty() }
                    .forEach { result.appendln("${config.getIndent(depth)}$it") }
        }

        // And now return the overall result
        return result.toString()
    }

    /**
     * Generates a Folder Name from the given string(s) that conforms to C style guidelines
     */
    fun createFolderName(vararg original: String): String {
        // Folder Names use UpperCamelCase
        return original.convertWordDelimiterConvention(NamingConvention.UPPER_CAMEL_CASE)
    }

    /**
     * Generates a File Name from the given string(s) that conforms to C style guidelines
     */
    fun createFileName(vararg original: String): String {
        // File Names use snake_case
        return original.convertWordDelimiterConvention(NamingConvention.SNAKE_CASE)
    }

    /**
     * Generates a Type Name from the given string(s) that conforms to C style guidelines
     */
    fun createTypeName(vararg original: String): String {
        // Type Names use UpperCamelCase
        return original.convertWordDelimiterConvention(NamingConvention.UPPER_CAMEL_CASE)
    }

    /**
     * Generates a Variable Name from the given string(s) that conforms to C style guidelines
     */
    fun createVariableName(vararg original: String): String {
        // Variable Names use snake_case
        return original.convertWordDelimiterConvention(NamingConvention.SNAKE_CASE)
    }

    /**
     * Generates a Function Name from the given string(s) that conforms to C style guidelines
     */
    fun createFunctionName(vararg original: String): String {
        // Function Names use UpperCamelCase
        return original.convertWordDelimiterConvention(NamingConvention.UPPER_CAMEL_CASE)
    }

    /**
     * Generates a Macro Name from the given string(s) that conforms to C style guidelines
     */
    fun createMacroName(vararg original: String): String {
        // Macro Names use UPPER_SNAKE_CASE
        return original.convertWordDelimiterConvention(NamingConvention.UPPER_SNAKE_CASE)
    }

    /**
     * Looks at a provided ParseTreeItem and its child and generates a C representation of the formula
     * If required, this method will add brackets around any operations that require them
     */
    private fun padOperand(item: ParseTreeItem, operand: ParseTreeItem, prefixData: PrefixData): String {
        // We need to look at the precedence of both this operator and the child to decide whether brackets are needed
        var precedence = item.getPrecedence()

        // An interesting case happens when one of the operators is not commutative and the child has 2 or more arguments
        // In this case we try to be safe by including brackets if they previously had the same precedence
        if((!operand.getCommutative() || !item.getCommutative()) && operand.getChildren().size > 1) {
            precedence--
        }

        // If the current operation's precedence is more than the childs, then we want brackets
        if(precedence < operand.getPrecedence())
            return "(" + generateCodeForParseTreeItem(operand, prefixData) + ")"

        // Special cases for C revolve around Or and And
        if(item is Or && operand is And)
            return "(" + generateCodeForParseTreeItem(operand, prefixData) + ")"

        // Otherwise no brackets
        return generateCodeForParseTreeItem(operand, prefixData, item)
    }

    /**
     * Generates a C representation of the given ParseTreeItem. PrefixData is used for determining any text that should
     * appear before any variable uses
     *
     * This will recursively go through the Parse Tree to generate the string, including adding brackets where necessary
     */
    fun generateCodeForParseTreeItem(item: ParseTreeItem, prefixData: PrefixData = PrefixData(""), parent: ParseTreeItem? = null): String {
        // For each possible ParseTreeItem we have a different output string that will be generated
        // We recursively call these generation functions until we reach the end of the tree
        return when (item) {
            is And -> padOperand(item, item.operandA, prefixData) + " && " + padOperand(item, item.operandB, prefixData)
            is Or -> padOperand(item, item.operandA, prefixData) + " || " + padOperand(item, item.operandB, prefixData)
            is Not -> "!" + padOperand(item, item.operandA, prefixData)
            is GreaterThan -> padOperand(item, item.operandA, prefixData) + " > " + padOperand(item, item.operandB, prefixData)
            is GreaterThanOrEqual -> padOperand(item, item.operandA, prefixData) + " >= " + padOperand(item, item.operandB, prefixData)
            is LessThanOrEqual -> padOperand(item, item.operandA, prefixData) + " <= " + padOperand(item, item.operandB, prefixData)
            is LessThan -> padOperand(item, item.operandA, prefixData) + " < " + padOperand(item, item.operandB, prefixData)
            is Equal -> padOperand(item, item.operandA, prefixData) + " == " + padOperand(item, item.operandB, prefixData)
            is NotEqual -> padOperand(item, item.operandA, prefixData) + " != " + padOperand(item, item.operandB, prefixData)
            is FunctionCall -> {
                // Functions have a lot of extra logic we need to do
                // Firstly we should check if it's a delayed function call, which will have special handling
                if(item.functionName == "delayed") {
                    // If the name matches, and it has the correct number of arguments
                    if(item.arguments.size == 2) {
                        // The first argument also should be a Variable
                        if(item.arguments[0] is me.nallen.modularCodeGeneration.parseTree.Variable) {
                            // Get the variable name we want to delay
                            val varName = item.arguments[0].getString()

                            // If we are able to delay this variable
                            if(prefixData.delayedVariableTypes.containsKey(varName)) {
                                // Generate the Delayable call for this variable. It requires the type of the variable,
                                // the name of the variable (to point to the struct), and the time that we want to fetch
                                // from
                                return "${createFunctionName("Delayable", generateCType(prefixData.delayedVariableTypes[varName]), "Get")}(" +
                                        "&${generateCodeForParseTreeItem(item.arguments[0], prefixData)}_delayed, " +
                                        "${generateCodeForParseTreeItem(item.arguments[1], prefixData)})"
                            }
                        }
                    }
                }

                // Otherwise, let's build a function
                val builder = StringBuilder()

                // If we're in run time parametrisation, we pass the automata struct into the function for parameters
                if(prefixData.requireSelfReferenceInFunctionCalls)
                    builder.append("me")

                // Now add each argument to the function
                for(argument in item.arguments) {
                    // If needed, deliminate by a comma
                    if(builder.isNotEmpty()) builder.append(", ")
                    builder.append(generateCodeForParseTreeItem(argument, prefixData))
                }

                // And then return the final function name
                return "${Utils.createFunctionName(item.functionName)}($builder)"
            }
            is Literal -> item.value
            is me.nallen.modularCodeGeneration.parseTree.Variable -> {
                // Variables also have a bit of extra logic that's needed

                // If the variable has a value assigned to it
                if(item.value != null)
                    // Then we want to just replace this with the string representing the value
                    return padOperand(parent ?: item, item.value!!, prefixData)
                else {
                    // Otherwise we want to generate this variable
                    // It may consist of data inside structs, separated by periods
                    val parts = item.name.split(".")
                    val builder = StringBuilder()

                    // For each part
                    for(part in parts) {
                        // If needed, deliminate by a period
                        if(builder.isNotEmpty()) builder.append(".")
                        // If we have a pre-determined value for this variable
                        if(prefixData.customVariableNames.containsKey(part))
                            // Then use that
                            builder.append(prefixData.customVariableNames[part]!!)
                        else
                            // Otherwise generate the C name for this variable
                            builder.append("${prefixData.prefix}${Utils.createVariableName(part)}")
                    }

                    // And return the final variable name
                    return builder.toString()
                }
            }
            is Plus -> padOperand(item, item.operandA, prefixData) + " + " + padOperand(item, item.operandB, prefixData)
            is Minus -> padOperand(item, item.operandA, prefixData) + " - " + padOperand(item, item.operandB, prefixData)
            is Negative -> "-" + padOperand(item, item.operandA, prefixData)
            is Multiply -> padOperand(item, item.operandA, prefixData) + " * " + padOperand(item, item.operandB, prefixData)
            is Divide -> padOperand(item, item.operandA, prefixData) + " / " + padOperand(item, item.operandB, prefixData)
            is SquareRoot -> "sqrt(" + generateCodeForParseTreeItem(item.operandA, prefixData) + ")"
            is Exponential -> "exp(" + generateCodeForParseTreeItem(item.operandA, prefixData) + ")"
        }
    }

    /**
     * Generates a C representation of the given Program. PrefixData is used for determining any text that should appear
     * before any variable uses
     *
     * This will recursively go through the Program to generate the string, including adding brackets where necessary
     */
    fun generateCodeForProgram(program: Program, config: Configuration, depth: Int = 0, prefixData: PrefixData = PrefixData("")): String {
        val builder = StringBuilder()

        // First, we want to declare and initialise any internal variables that exist in this program
        program.variables.filter({it.locality == ParseTreeLocality.INTERNAL})
                .filterNot { prefixData.customVariableNames.containsKey(it.name) }
                .forEach { builder.appendln("${config.getIndent(depth)}${Utils.generateCType(it.type)} ${Utils.createVariableName(it.name)};") }
        if(builder.isNotEmpty())
            builder.appendln()

        // Now, we need to go through each line
        program.lines
                .map {
                    // And convert the ProgramLine into a string representation
                    // Note that some of these will recursively call this method, as they contain their own bodies
                    // (such as If statements)
                    when(it) {
                        is Statement -> "${Utils.generateCodeForParseTreeItem(it.logic, prefixData)};"
                        is Assignment -> "${Utils.generateCodeForParseTreeItem(it.variableName, prefixData)} = ${Utils.generateCodeForParseTreeItem(it.variableValue, prefixData)};"
                        is Return -> "return ${Utils.generateCodeForParseTreeItem(it.logic, prefixData)};"
                        is IfStatement -> "if(${Utils.generateCodeForParseTreeItem(it.condition, prefixData)}) {\n${Utils.generateCodeForProgram(it.body, config, 1, prefixData)}\n}"
                        is ElseIfStatement -> "else if(${Utils.generateCodeForParseTreeItem(it.condition, prefixData)}) {\n${Utils.generateCodeForProgram(it.body, config, 1, prefixData)}\n}"
                        is ElseStatement -> "else {\n${Utils.generateCodeForProgram(it.body, config, 1, prefixData)}\n}"
                    }
                }
                .forEach { builder.appendln(it.prependIndent(config.getIndent(depth))) }

        // And return the total program
        return builder.toString().trimEnd()
    }

    /**
     * A class that contains various information about variables and functions used when generating code
     */
    data class PrefixData(
            // A prefix that should be placed before all variable names
            val prefix: String,

            // Whether or not to require the first argument of a custom function call to be a self-reference to the
            // object's struct
            val requireSelfReferenceInFunctionCalls: Boolean = false,

            // A set of the variables that can be delayed, and their types
            val delayedVariableTypes: Map<String, VariableType> = HashMap(),

            // A set of variables whose names should be something other than the style convention
            val customVariableNames: Map<String, String> = DEFAULT_CUSTOM_VARIABLES
    )
}