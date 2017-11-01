package me.nallen.modularCodeGeneration.codeGen

data class Configuration(
        val indentSize: Int = 4,
        val parametrisationMethod: ParameterisationMethod = ParameterisationMethod.COMPILE_TIME
) {
    fun getIndent(depth: Int = 1): String {
        var indent = "\t"
        if(indentSize > 0)
            indent = " ".repeat(indentSize)

        return indent.repeat(depth)
    }
}

enum class ParameterisationMethod {
    RUN_TIME, COMPILE_TIME
}