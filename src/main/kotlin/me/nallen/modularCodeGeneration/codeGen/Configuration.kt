package me.nallen.modularCodeGeneration.codeGen

data class Configuration(
        val indentSize: Int = 4,
        val parametrisationMethod: ParametrisationMethod = ParametrisationMethod.COMPILE_TIME,
        val maximumInterTransitions: Int = 1,
        val requireOneIntraTransitionPerTick: Boolean = false
) {
    fun getIndent(depth: Int = 1): String {
        val indent: String
        if(indentSize >= 0)
            indent = " ".repeat(indentSize)
        else
            indent = "\t".repeat(-1 * indentSize)

        return indent.repeat(depth)
    }
}

enum class ParametrisationMethod {
    RUN_TIME, COMPILE_TIME
}