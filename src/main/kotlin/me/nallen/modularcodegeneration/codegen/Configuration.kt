package me.nallen.modularcodegeneration.codegen

/**
 * Captures all the configuration settings that can be changed to modify the code generation output
 */
data class Configuration(
        // The indentation size (in spaces) for the generated code. Use negative numbers for tabs
        val indentSize: Int = 4,

        // The settings for the execution properties of the generated code such as step size and simulation time
        val execution: Execution = Execution(),

        // The settings for the logging properties of the generated code
        val logging: Logging = Logging(),

        // The method to use for parametrisation when code is generated
        val parametrisationMethod: ParametrisationMethod = ParametrisationMethod.COMPILE_TIME,

        // The maximum number of inter-location transitions that can be taken within each "step". In Hybrid Automata
        // semantics these transitions should be instantaneous and this aims to replicate that to some degree.
        val maximumInterTransitions: Int = 1,

        // Whether or not to require an intra-location transition (i.e. ODEs) within each "step". The evolution of ODEs
        // is the only aspect of Hybrid Automata that should take any time
        val requireOneIntraTransitionPerTick: Boolean = false
) {
    /**
     * Returns a string that represents the indent according to the configuration settings
     *
     * Depth signifies the number of indents that should be generated
     */
    fun getIndent(depth: Int = 1): String {
        // Check whether we're indenting by spaces or tabs
        val indent = if(indentSize >= 0)
            // Positive numbers are spaces
            " ".repeat(indentSize)
        else
            // Negative numbers are tabs
            "\t".repeat(-1 * indentSize)

        // And then repeat by the requested depth
        return indent.repeat(depth)
    }

    val runTimeParametrisation: Boolean = parametrisationMethod == ParametrisationMethod.RUN_TIME
    val compileTimeParametrisation: Boolean = parametrisationMethod == ParametrisationMethod.COMPILE_TIME
}

/**
 * A set of options that determine the execution time and fidelity of the generated code
 */
data class Execution(
        // The step size that is used for discretising the ODEs during execution, in seconds
        val stepSize: Double = 0.001,

        // The time that will be simulated when the generated code is executed, in seconds
        val simulationTime: Double = 10.0
)

/**
 * A set of options that determine which information is logged when the generated code is executed
 */
data class Logging(
        // Whether or not to enable logging of outputs
        val enable: Boolean = true,

        // The interval at which to output log results to the file. For best results this should be an integer multiple
        // of the step size
        val interval: Double? = null,

        // The file where the logging output should be placed
        val file: String = "out.csv",

        // The list of fields to output when logging
        val fields: List<String>? = null
)

/**
 * An enum that represents the method used for parametrising the Hybrid Automata Definitions
 */
enum class ParametrisationMethod {
    RUN_TIME, COMPILE_TIME
}