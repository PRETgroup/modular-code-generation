package me.nallen.modularCodeGeneration.codeGen

data class Configuration(
        val indentSize: Int = 4,
        val parametrisationMethod: ParametrisationMethod = ParametrisationMethod.COMPILE_TIME,
        val maximumInterTransitions: Int = 1,
        val runIntraTransitionOnEntry: Boolean = false
) {
    fun getIndent(depth: Int = 1): String {
        var indent = "\t"
        if(indentSize > 0)
            indent = " ".repeat(indentSize)

        return indent.repeat(depth)
    }
}

enum class ParametrisationMethod {
    RUN_TIME, COMPILE_TIME
}