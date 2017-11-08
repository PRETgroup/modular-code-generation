package me.nallen.modularCodeGeneration.codeGen

data class Configuration(
        val indentSize: Int = 4,
        val execution: Execution = Execution(),
        val logging: Logging = Logging(),
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

data class Execution(
        val stepSize: Double = 0.001,
        val simulationTime: Double = 10.0
)

data class Logging(
        val enable: Boolean = true,
        val interval: Double? = null,
        val file: String = "out.csv",
        val fields: List<String>? = null
)

enum class ParametrisationMethod {
    RUN_TIME, COMPILE_TIME
}