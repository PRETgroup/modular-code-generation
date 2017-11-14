package me.nallen.modularCodeGeneration.parseTree

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

data class Program(
        val lines: ArrayList<ProgramLine> = ArrayList(),

        val variables: ArrayList<VariableDeclaration> = ArrayList()
) {
    companion object Factory {
        @JsonCreator @JvmStatic
        fun generate(input: String): Program = generateProgramFromString(input)
    }

    @JsonValue
    fun getString(): String {
        return this.generateString()
    }

    private fun addVariable(item: String, type: VariableType, locality: Locality = Locality.INTERNAL, default: ParseTreeItem? = null, knownVariables: HashMap<String, VariableType> = LinkedHashMap(), knownFunctions: Map<String, VariableType?> = LinkedHashMap()): Program {
        if(!variables.any({it.name == item})) {
            variables.add(VariableDeclaration(item, type, locality, default))

            if(default != null)
                checkParseTreeForNewVariable(default, knownVariables, knownFunctions)
        }

        return this
    }

    fun collectVariables(existing: List<VariableDeclaration> = ArrayList(), knownFunctionTypes: Map<String, VariableType?> = LinkedHashMap()): Program {
        val knownVariables = LinkedHashMap<String, VariableType>()

        for(item in existing) {
            knownVariables.put(item.name, item.type)
        }

        for(item in existing) {
            addVariable(item.name, item.type, Locality.EXTERNAL_INPUT, item.defaultValue, knownVariables, knownFunctionTypes)
        }


        val bodiesToParse = ArrayList<Program>()
        for(line in lines) {
            when(line) {
                is Statement -> checkParseTreeForNewVariable(line.logic, knownVariables, knownFunctionTypes)
                is Assignment -> {
                    if(!knownVariables.containsKey(line.variableName.name))
                        knownVariables.put(line.variableName.name, line.variableValue.getOperationResultType(knownVariables, knownFunctionTypes))

                    checkParseTreeForNewVariable(line.variableName, knownVariables, knownFunctionTypes)
                    checkParseTreeForNewVariable(line.variableValue, knownVariables, knownFunctionTypes)
                }
                is Return -> checkParseTreeForNewVariable(line.logic, knownVariables, knownFunctionTypes)
                is IfStatement -> {
                    checkParseTreeForNewVariable(line.condition, knownVariables, knownFunctionTypes)
                    bodiesToParse.add(line.body)
                }
                is ElseIfStatement -> {
                    checkParseTreeForNewVariable(line.condition, knownVariables, knownFunctionTypes)
                    bodiesToParse.add(line.body)
                }
                is ElseStatement -> bodiesToParse.add(line.body)
            }
        }

        for(body in bodiesToParse) {
            body.collectVariables(variables, knownFunctionTypes)
        }

        return this
    }

    fun getReturnType(knownFunctionTypes: Map<String, VariableType?> = LinkedHashMap()): VariableType? {
        val bodiesToParse = ArrayList<Program>()

        val variableTypeMap = LinkedHashMap<String, VariableType>()
        for(variable in variables) {
            variableTypeMap.put(variable.name, variable.type)
        }

        var currentReturnType: VariableType? = null

        for(line in lines) {
            when(line) {
                is Return -> currentReturnType = combineReturnTypes(currentReturnType, line.logic.getOperationResultType(variableTypeMap, knownFunctionTypes))
                is IfStatement -> {
                    bodiesToParse.add(line.body)
                }
                is ElseIfStatement -> {
                    bodiesToParse.add(line.body)
                }
                is ElseStatement -> bodiesToParse.add(line.body)
            }
        }

        for(body in bodiesToParse) {
            currentReturnType = combineReturnTypes(currentReturnType, body.getReturnType(knownFunctionTypes))
        }

        return currentReturnType
    }

    private fun combineReturnTypes(a: VariableType?, b: VariableType?): VariableType? {
        if(a == null)
            return b

        if(b == null)
            return a

        if(a != b)
            throw IllegalArgumentException("Error in return types!")

        return a
    }

    private fun checkParseTreeForNewVariable(item: ParseTreeItem, knownVariables: HashMap<String, VariableType>, knownFunctions: Map<String, VariableType?>) {
        if(item is Variable) {
            addVariable(item.name, item.getOperationResultType(knownVariables, knownFunctions))

            if(!knownVariables.containsKey(item.name))
                knownVariables.put(item.name, variables.first({it.name == item.name}).type)
        }

        for(child in item.getChildren()) {
            checkParseTreeForNewVariable(child, knownVariables, knownFunctions)
        }
    }
}

data class VariableDeclaration(
        var name: String,
        var type: VariableType,
        var locality: Locality,
        var defaultValue: ParseTreeItem? = null
)

enum class Locality {
    INTERNAL, EXTERNAL_INPUT
}

sealed class ProgramLine(var type: String)

data class Statement(var logic: ParseTreeItem): ProgramLine("statement")
data class Assignment(var variableName: Variable, var variableValue: ParseTreeItem): ProgramLine("assignment")
data class Return(var logic: ParseTreeItem): ProgramLine("return")
data class IfStatement(var condition: ParseTreeItem, var body: Program): ProgramLine("ifStatement")
data class ElseStatement(var body: Program): ProgramLine("elseStatement")
data class ElseIfStatement(var condition: ParseTreeItem, var body: Program): ProgramLine("elseIfStatement")

fun generateProgramFromString(input: String): Program {
    val program = Program()

    val lines = input.lines()

    var skip = 0
    var i = 0
    for(line in lines) {
        i++

        if(skip > 0) {
            skip--
            continue
        }

        if(line.isNotBlank()) {
            var programLine: ProgramLine? = null

            val conditionalRegex = Regex("^\\s*((if|else(\\s*)if)\\s*\\((.*)\\)|else)\\s*\\{\\s*\$")
            val returnRegex = Regex("^\\s*return\\s+(.*)\\s*$")
            val assignmentRegex = Regex("^\\s*([-_a-zA-Z0-9]+)\\s*=\\s*(.*)\\s*$")

            val match = conditionalRegex.matchEntire(line)
            if(match != null) {
                val bodyText = getTextUntilNextMatchingCloseBracket(lines.slice(IntRange(i, lines.size-1)).joinToString("\n"))
                skip = bodyText.count({it == '\n'})+1

                val body = generateProgramFromString(bodyText)
                if(match.groupValues[1] == "else") {
                    programLine = ElseStatement(body)
                }
                else {
                    val condition = ParseTreeItem.generate(match.groupValues[4])
                    if(match.groupValues[2] == "if")
                        programLine = IfStatement(condition, body)
                    else if(match.groupValues[2].startsWith("else"))
                        programLine = ElseIfStatement(condition, body)
                }
            }
            else {
                val match = returnRegex.matchEntire(line)
                if(match != null) {
                    programLine = Return(ParseTreeItem.Factory.generate(match.groupValues[1]))
                }
                else {
                    val match = assignmentRegex.matchEntire(line)
                    if(match != null) {
                        programLine = Assignment(Variable(match.groupValues[1]), ParseTreeItem.Factory.generate(match.groupValues[2]))
                    }
                    else {
                        programLine = Statement(ParseTreeItem.Factory.generate(line))
                    }
                }
            }

            if(programLine != null)
                program.lines.add(programLine)
        }
    }

    return program
}

fun getTextUntilNextMatchingCloseBracket(input: String): String {
    var bracketsToFind = 1;
    for(i in 0 until input.length) {
        if(input[i] == '{')
            bracketsToFind++
        else if(input[i] == '}')
            bracketsToFind--

        if(bracketsToFind == 0)
            return input.substring(0, i)
    }

    throw IllegalArgumentException("Invalid program!")
}

fun Program.generateString(): String {
    val builder = StringBuilder()

    for(line in lines) {
        val lineString = when(line) {
            is Statement -> line.logic.generateString()
            is Assignment -> "${line.variableName.generateString()} = ${line.variableValue.generateString()}"
            is Return -> "return ${line.logic.generateString()}"
            is IfStatement -> "if(${line.condition.generateString()}) {\n${line.body.generateString().prependIndent("  ")}\n}"
            is ElseIfStatement -> "else if(${line.condition.generateString()}) {\n${line.body.generateString().prependIndent("  ")}\n}"
            is ElseStatement -> "else {\n${line.body.generateString().prependIndent("  ")}\n}"
        }

        builder.appendln(lineString)
    }

    return builder.toString().trimEnd()
}

fun Program.setParameterValue(key: String, value: ParseTreeItem): Program {
    for(variable in variables) {
        variable.defaultValue?.setParameterValue(key, value)
    }

    for(line in lines) {
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
        }
    }

    return this
}