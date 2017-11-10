package me.nallen.modularCodeGeneration.parseTree

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

data class Program(
        val lines: ArrayList<ProgramLine> = ArrayList(),

        val variables: ArrayList<Variable> = ArrayList()
) {
    companion object Factory {
        @JsonCreator @JvmStatic
        fun generate(input: String): Program = generateProgramFromString(input)
    }

    @JsonValue
    override fun toString(): String {
        return this.generateString()
    }
}

sealed class ProgramLine(var type: String)

data class Statement(var logic: ParseTreeItem): ProgramLine("statement")
data class Assignment(var variableName: String, var variableValue: ParseTreeItem): ProgramLine("assignment")
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
                        programLine = Assignment(match.groupValues[1], ParseTreeItem.Factory.generate(match.groupValues[2]))
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
            is Assignment -> "${line.variableName} = ${line.variableValue.generateString()}"
            is Return -> "return ${line.logic.generateString()}"
            is IfStatement -> "if(${line.condition.generateString()}) {\n${line.body.generateString().prependIndent("  ")}\n}"
            is ElseIfStatement -> "else if(${line.condition.generateString()}) {\n${line.body.generateString().prependIndent("  ")}\n}"
            is ElseStatement -> "else {\n${line.body.generateString().prependIndent("  ")}\n}"
        }

        builder.appendln(lineString)
    }

    return builder.toString().trimEnd()
}