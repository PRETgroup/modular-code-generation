package me.nallen.modularCodeGeneration.codeGen.vhdl

import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.hybridAutomata.HybridItem
import me.nallen.modularCodeGeneration.hybridAutomata.Locality
import me.nallen.modularCodeGeneration.hybridAutomata.Variable
import me.nallen.modularCodeGeneration.parseTree.*
import me.nallen.modularCodeGeneration.utils.NamingConvention
import me.nallen.modularCodeGeneration.utils.convertWordDelimiterConvention
import kotlin.math.roundToInt

typealias ParseTreeLocality = me.nallen.modularCodeGeneration.parseTree.Locality

/**
 * A set of utilities for C Code generation regarding types, ParseTree generation, and naming conventions
 */
object Utils {
    // If something uses STEP_SIZE, we don't want to throw an error when there's no default value for it, because it's
    // defined in another file
    val DEFAULT_CUSTOM_VARIABLES = mapOf("STEP_SIZE" to "STEP_SIZE")

    fun convertToFixedPoint(number: Number, fractionalBits: Int = 16): Int {
        return (number.toDouble() * Math.pow(2.0, fractionalBits.toDouble())).roundToInt()
    }

    /**
     * Convert our VariableType to a VHDL type
     */
    fun generateVHDLType(type: VariableType?): String {
        // Switch on the type, and return the appropriate VHDL type
        return when(type) {
            null -> "void"
            VariableType.BOOLEAN -> "std_logic"
            VariableType.REAL -> "signed(31 downto 0)"
        }
    }

    fun generateDefaultInitForType(type: VariableType?): String {
        // Switch on the type, and return the appropriate default initial value
        return when(type) {
            null -> "void"
            VariableType.BOOLEAN -> "'0'"
            VariableType.REAL -> "(others => '0')"
        }
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
            is And -> padOperand(item, item.operandA, prefixData) + " and " + padOperand(item, item.operandB, prefixData)
            is Or -> padOperand(item, item.operandA, prefixData) + " or " + padOperand(item, item.operandB, prefixData)
            is Not -> "not " + padOperand(item, item.operandA, prefixData)
            is GreaterThan -> padOperand(item, item.operandA, prefixData) + " > " + padOperand(item, item.operandB, prefixData)
            is GreaterThanOrEqual -> padOperand(item, item.operandA, prefixData) + " >= " + padOperand(item, item.operandB, prefixData)
            is LessThanOrEqual -> padOperand(item, item.operandA, prefixData) + " <= " + padOperand(item, item.operandB, prefixData)
            is LessThan -> padOperand(item, item.operandA, prefixData) + " < " + padOperand(item, item.operandB, prefixData)
            is Equal -> padOperand(item, item.operandA, prefixData) + " = " + padOperand(item, item.operandB, prefixData)
            is NotEqual -> padOperand(item, item.operandA, prefixData) + " != " + padOperand(item, item.operandB, prefixData)
            is FunctionCall -> throw NotImplementedError("Custom functions are currently not supported in VHDL Generation")
            is Literal -> {
                if(item.value.toDoubleOrNull() != null) {
                    "to_signed(${convertToFixedPoint(item.value.toDouble())}, 32)"
                }
                else if(item.value.matches(Regex("true|false", RegexOption.IGNORE_CASE))) {
                    if(item.value.toBoolean())
                        "'1'"
                    else
                        "'0'"
                }
                else {
                    item.value
                }
            }
            is me.nallen.modularCodeGeneration.parseTree.Variable -> {
                // Variables also have a bit of extra logic that's needed

                // If the variable has a value assigned to it
                if(item.value != null)
                    // Then we want to just replace this with the string representing the value
                    return padOperand(parent ?: item, item.value!!, prefixData)
                else {
                    // Otherwise we want to generate this variable

                    // If we have a pre-determined value for this variable
                    if(prefixData.customVariableNames.containsKey(item.name))
                        // Then use that
                        return prefixData.customVariableNames[item.name]!!

                    // It may consist of data inside structs, separated by periods
                    val parts = item.name.split(".")
                    val builder = StringBuilder()

                    // For each part
                    var first = true
                    for(part in parts) {
                        // If needed, deliminate by an underscore
                        if(!first) builder.append("_")

                        // Generate the C name for this variable
                        if(first)
                            builder.append("${prefixData.prefix}${Utils.createVariableName(part)}")
                        else
                            builder.append(Utils.createVariableName(part))

                        first = false
                    }

                    // And return the final variable name
                    return builder.toString()
                }
            }
            is Plus -> padOperand(item, item.operandA, prefixData) + " + " + padOperand(item, item.operandB, prefixData)
            is Minus -> padOperand(item, item.operandA, prefixData) + " - " + padOperand(item, item.operandB, prefixData)
            is Negative -> "-" + padOperand(item, item.operandA, prefixData)
            is Multiply -> "FP_MULT(" + padOperand(item, item.operandA, prefixData) + ", " + padOperand(item, item.operandB, prefixData) + ")"
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
                .forEach { builder.appendln("${config.getIndent(depth)}${Utils.generateVHDLType(it.type)} ${Utils.createVariableName(it.name)};") }
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

            // A set of variables whose names should be something other than the style convention
            val customVariableNames: Map<String, String> = DEFAULT_CUSTOM_VARIABLES
    )
}