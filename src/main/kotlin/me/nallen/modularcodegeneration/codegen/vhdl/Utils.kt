package me.nallen.modularcodegeneration.codegen.vhdl

import me.nallen.modularcodegeneration.hybridautomata.Locality
import me.nallen.modularcodegeneration.hybridautomata.Variable
import me.nallen.modularcodegeneration.parsetree.*
import me.nallen.modularcodegeneration.utils.NamingConvention
import me.nallen.modularcodegeneration.utils.convertWordDelimiterConvention

typealias ParseTreeLocality = me.nallen.modularcodegeneration.parsetree.Locality

/**
 * A set of utilities for C Code generation regarding types, ParseTree generation, and naming conventions
 */
object Utils {
    // If something uses STEP_SIZE, we don't want to throw an error when there's no default value for it, because it's
    // defined in another file
    val DEFAULT_CUSTOM_VARIABLES = mapOf("STEP_SIZE" to "STEP_SIZE")

    // A list of keywords that we want to avoid when generating variables, types, signals, etc.
    private val KEYWORDS = arrayOf("abs",
            "access",
            "after",
            "alias",
            "all",
            "and",
            "architecture",
            "array",
            "assert",
            "attribute",
            "begin",
            "block",
            "body",
            "buffer",
            "bus",
            "case",
            "component",
            "configuration",
            "constant",
            "disconnect",
            "downto",
            "else",
            "elsif",
            "end",
            "entity",
            "exit",
            "file",
            "for",
            "function",
            "generate",
            "generic",
            "group",
            "guarded",
            "if",
            "impure",
            "in",
            "inertial",
            "inout",
            "is",
            "label",
            "library",
            "linkage",
            "literal",
            "loop",
            "map",
            "mod",
            "nand",
            "new",
            "next",
            "nor",
            "not",
            "null",
            "of",
            "on",
            "open",
            "or",
            "others",
            "out",
            "package",
            "port",
            "postponed",
            "procedure",
            "process",
            "pure",
            "range",
            "record",
            "register",
            "reject",
            "rem",
            "report",
            "return",
            "rol",
            "ror",
            "select",
            "severity",
            "shared",
            "signal",
            "sla",
            "sll",
            "sra",
            "srl",
            "subtype",
            "then",
            "to",
            "transport",
            "type",
            "unaffected",
            "units",
            "until",
            "use",
            "variable",
            "wait",
            "when",
            "while",
            "with",
            "xnor",
            "xor")

    /**
     * Convert our VariableType to a VHDL type
     */
    fun generateVHDLType(type: VariableType?): String {
        // Switch on the type, and return the appropriate VHDL type
        return when(type) {
            null -> "void"
            VariableType.BOOLEAN -> "boolean"
            VariableType.REAL -> "signed(31 downto 0)"
            VariableType.INTEGER -> "integer"
            else -> throw NotImplementedError("Unable to generate code for requested type '$type'")
        }
    }

    /**
     * Converts a VariableType to a basic VHDL type (one which doesn't contain ranges)
     */
    fun generateBasicVHDLType(type: VariableType?): String {
        // Switch on the type, and return the appropriate VHDL type
        return when(type) {
            null -> "void"
            VariableType.BOOLEAN -> "boolean"
            VariableType.REAL -> "signed"
            VariableType.INTEGER -> "integer"
            else -> throw NotImplementedError("Unable to generate code for requested type '$type'")
        }
    }

    /**
     * Get the default initiailisation value for a given VHDL Type
     */
    fun generateDefaultInitForType(type: VariableType?): String {
        // Switch on the type, and return the appropriate default initial value
        return when(type) {
            null -> "void"
            VariableType.BOOLEAN -> "false"
            VariableType.REAL -> "(others => '0')"
            VariableType.INTEGER -> "0"
            else -> throw NotImplementedError("Unable to generate code for requested type '$type'")
        }
    }

    /**
     * Generates a Folder Name from the given string(s) that conforms to C style guidelines
     */
    fun createFolderName(vararg original: String): String {
        // Folder Names use UpperCamelCase
        var out = original.convertWordDelimiterConvention(NamingConvention.UPPER_CAMEL_CASE)
        if(KEYWORDS.contains(out.toLowerCase())) {
            out = createFolderName("f", *original)
        }

        return out
    }

    /**
     * Generates a File Name from the given string(s) that conforms to C style guidelines
     */
    fun createFileName(vararg original: String): String {
        // File Names use snake_case
        var out = original.convertWordDelimiterConvention(NamingConvention.SNAKE_CASE)
        if(KEYWORDS.contains(out.toLowerCase())) {
            out = createFileName("f", *original)
        }

        return out
    }

    /**
     * Generates a Type Name from the given string(s) that conforms to C style guidelines
     */
    fun createTypeName(vararg original: String): String {
        // Type Names use UpperCamelCase
        var out = original.convertWordDelimiterConvention(NamingConvention.UPPER_CAMEL_CASE)
        if(KEYWORDS.contains(out.toLowerCase())) {
            out = createTypeName("p", *original)
        }

        return out
    }

    /**
     * Generates a Variable Name from the given string(s) that conforms to C style guidelines
     */
    fun createVariableName(vararg original: String): String {
        // Variable Names use snake_case
        var out = original.convertWordDelimiterConvention(NamingConvention.SNAKE_CASE)
        if(KEYWORDS.contains(out.toLowerCase())) {
            out = createVariableName("p", *original)
        }

        return out
    }

    /**
     * Generates a Function Name from the given string(s) that conforms to C style guidelines
     */
    fun createFunctionName(vararg original: String): String {
        // Function Names use UpperCamelCase
        var out = original.convertWordDelimiterConvention(NamingConvention.UPPER_CAMEL_CASE)
        if(KEYWORDS.contains(out.toLowerCase())) {
            out = createFunctionName("p", *original)
        }

        return out
    }

    /**
     * Generates a Macro Name from the given string(s) that conforms to C style guidelines
     */
    fun createMacroName(vararg original: String): String {
        // Macro Names use UPPER_SNAKE_CASE
        var out = original.convertWordDelimiterConvention(NamingConvention.UPPER_SNAKE_CASE)
        if(KEYWORDS.contains(out.toLowerCase())) {
            out = createMacroName("p", *original)
        }

        return out
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
            is FunctionCall -> {
                // Otherwise, let's build a function
                val builder = StringBuilder()

                // Now add each argument to the function
                for(argument in item.arguments) {
                    // If needed, deliminate by a comma
                    if(builder.isNotEmpty()) builder.append(", ")
                    builder.append(generateCodeForParseTreeItem(argument, prefixData))
                }

                if(prefixData.extraFunctionParams.containsKey(item.functionName)) {
                    for(argument in prefixData.extraFunctionParams.getValue(item.functionName)) {
                        // If needed, deliminate by a comma
                        if(builder.isNotEmpty()) builder.append(", ")
                        builder.append(generateCodeForParseTreeItem(argument, prefixData))
                    }
                }

                // And then return the final function name
                return "${createFunctionName(item.functionName)}($builder)"
            }
            is Literal -> {
                // If the literal can be a double, then we'll use our custom function to create a fixed-point number
                if(item.value.toDoubleOrNull() != null) {
                    "CREATE_FP(${item.value.toDouble()})"
                }
                else {
                    // Otherwise it's probably a boolean, so just use it as such
                    item.value
                }
            }
            is me.nallen.modularcodegeneration.parsetree.Variable -> {
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
                        return prefixData.customVariableNames.getValue(item.name)

                    // It may consist of data inside structs, separated by periods
                    val parts = item.name.split(".")

                    return createVariableName(*parts.toTypedArray())
                }
            }
            is Plus -> padOperand(item, item.operandA, prefixData) + " + " + padOperand(item, item.operandB, prefixData)
            is Minus -> padOperand(item, item.operandA, prefixData) + " - " + padOperand(item, item.operandB, prefixData)
            is Negative -> {
                // If we have a negative number then we should just put the negative sign inside the FP conversion,
                // otherwise we can just chuck in a negative sign as usual
                if(item.operandA is Literal && (item.operandA as Literal).value.toDoubleOrNull() != null) {
                    padOperand(item, Literal((-1 * (item.operandA as Literal).value.toDouble()).toString()), prefixData)
                }
                else {
                    "-" + padOperand(item, item.operandA, prefixData)
                }
            }
            is Multiply -> "FP_MULT(" + padOperand(item, item.operandA, prefixData) + ", " + padOperand(item, item.operandB, prefixData) + ")"
            is Divide -> "FP_DIV(" + padOperand(item, item.operandA, prefixData) + ", " + padOperand(item, item.operandB, prefixData) + ")"
            is SquareRoot -> throw NotImplementedError("Square Root is currently not supported in VHDL Generation")
            is Exponential -> throw NotImplementedError("Exponential is currently not supported in VHDL Generation")
            is Sine -> throw NotImplementedError("Trigonometric functions are currently not supported in VHDL Generation")
            is Cosine -> throw NotImplementedError("Trigonometric functions are currently not supported in VHDL Generation")
            is Tangent -> throw NotImplementedError("Trigonometric functions are currently not supported in VHDL Generation")
            is Pi -> "CREATE_FP(${Math.PI})"
        }
    }

    /**
     * Generates a C representation of the given Program. PrefixData is used for determining any text that should appear
     * before any variable uses
     *
     * This will recursively go through the Program to generate the string, including adding brackets where necessary
     */
    fun generateCodeForProgram(program: Program, depth: Int = 0, prefixData: PrefixData = PrefixData("")): String {
        val builder = StringBuilder()

        val indent = "    ".repeat(depth)

        // Now, we need to go through each line
        program.lines.forEachIndexed { index, line ->
            // And convert the ProgramLine into a string representation
            // Note that some of these will recursively call this method, as they contain their own bodies
            // (such as If statements)
            builder.appendln(when(line) {
                is Statement -> "${Utils.generateCodeForParseTreeItem(line.logic, prefixData)};"
                is Assignment -> "${Utils.generateCodeForParseTreeItem(line.variableName, prefixData)} := ${Utils.generateCodeForParseTreeItem(line.variableValue, prefixData)};"
                is Return -> "return ${Utils.generateCodeForParseTreeItem(line.logic, prefixData)};"
                is IfStatement -> "if ${Utils.generateCodeForParseTreeItem(line.condition, prefixData)} then\n${Utils.generateCodeForProgram(line.body, 1, prefixData)}\n"
                is ElseIfStatement -> "elsif ${Utils.generateCodeForParseTreeItem(line.condition, prefixData)} then\n${Utils.generateCodeForProgram(line.body, 1, prefixData)}\n"
                is ElseStatement -> "else\n${Utils.generateCodeForProgram(line.body, 1, prefixData)}\n"
            }.prependIndent(indent))

            // If we're in a conditional block we need to check if we need to add an end
            if(line is IfStatement || line is ElseIfStatement || line is ElseStatement) {
                if(index+1 >= program.lines.size || !(program.lines[index+1] is ElseIfStatement || program.lines[index+1] is ElseStatement)) {
                    builder.appendln("end if;\n".prependIndent(indent))
                }
            }
        }

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
            val customVariableNames: Map<String, String> = DEFAULT_CUSTOM_VARIABLES,

            // A set of extra parameters that need to be added to functions when we call them
            val extraFunctionParams: Map<String, List<ParseTreeItem>> = HashMap()
    )


    /**
     * A class which is capable of storing all the information that we'd need about any variable / signal / IO that
     * would be defined in the generated VHDL code
     */
    data class VariableObject(
            // The locality of this (Inputs, Outputs, Internal, etc.)
            var locality: String,

            // For IO signals, the direction of the variable ("in", "out")
            var direction: String,

            // The type of the variable to be defined
            var type: String,

            // The name for this variable if it is an input signal
            var io: String,

            // The name for this variable if it is an internal signal
            var signal: String,

            // The name for this variable if it is a process variable
            var variable: String,

            // The initial (default) value to be used for this variable
            var initialValue: String,

            // Any string that should be used as a comment for the instantiation of the variable (or blank)
            var initialValueString: String
    ) {
        companion object {
            /**
             * Method to create the VariableObject for a given Variable
             */
            fun create(variable: Variable, valuations: Map<String, ParseTreeItem> = HashMap(), runtimeParametrisation: Boolean = false, prefixData: PrefixData = PrefixData("")): VariableObject {
                // The default value could be either contained within the Variable itself, or in a map of default values
                // e.g. initialisation function, etc.
                val default: ParseTreeItem? = valuations[variable.name] ?: variable.defaultValue

                // Let's start with the default value
                var defaultValue: Any = Utils.generateDefaultInitForType(variable.type)
                if (default != null) {
                    // If we have something specific to set it to, then let's replace it with that
                    defaultValue = try {
                        default.evaluate()
                    } catch (e: IllegalArgumentException) {
                        Utils.generateCodeForParseTreeItem(default, prefixData)
                    }

                    // We want a string version of the default value, so let's convert it
                    if (defaultValue is Boolean)
                        // As a Boolean
                        defaultValue = if(defaultValue) { "true" } else { "false" }
                    else if (defaultValue is Double)
                        // Or as a Number
                        defaultValue = "CREATE_FP($defaultValue)"
                }

                // If we have run-time parametrisation then we need parameters to also be external inputs
                val locality = if(runtimeParametrisation && variable.locality == Locality.PARAMETER)
                    Locality.EXTERNAL_INPUT
                else
                    // Otherwise we just use the same locality
                    variable.locality

                // Now let's make the object!
                return VariableObject(
                        variable.locality.getTextualName(),
                        locality.getShortName().toLowerCase(),
                        Utils.generateVHDLType(variable.type),
                        Utils.createVariableName(variable.name, variable.locality.getShortName()),
                        Utils.createVariableName(variable.name),
                        Utils.createVariableName(variable.name, "update"),
                        defaultValue.toString(),
                        ""
                )
            }
        }
    }
}