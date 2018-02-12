package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.CodeGenManager
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.LoggingField
import me.nallen.modularCodeGeneration.codeGen.ParametrisationMethod
import me.nallen.modularCodeGeneration.hybridAutomata.HybridItem
import me.nallen.modularCodeGeneration.hybridAutomata.HybridNetwork

/**
 * The class that contains methods to do with the generation of the main runnable file for the system
 */
object RunnableGenerator {
    private var item: HybridItem = HybridNetwork()
    private var config: Configuration = Configuration()

    private var requireSelfReferenceInFunctionCalls: Boolean = false
    private var toLog: List<LoggingField> = ArrayList()

    /**
     * Generates a string that represents the main runnable file for the system.
     */
    fun generate(item: HybridItem, config: Configuration = Configuration()): String {
        this.item = item
        this.config = config

        // Whether or not we need to include self references in custom functions
        this.requireSelfReferenceInFunctionCalls = config.parametrisationMethod == ParametrisationMethod.RUN_TIME

        // Collect all the fields that we will need to log (if any)
        toLog = CodeGenManager.collectFieldsToLog(item, config)

        // Now let's build the runnable
        val result = StringBuilder()

        // Start with all the includes at the top of the file
        result.appendln("#include <stdint.h>")
        result.appendln("#include <stdlib.h>")
        result.appendln("#include <stdio.h>")
        result.appendln("#include <string.h>")
        result.appendln()

        // The root file that needs to be included will be in a different location depending on if it's a Network or a
        // single Automata
        if(item is HybridNetwork)
            result.appendln("#include \"${Utils.createFolderName(item.name, "Network")}/${Utils.createFileName(item.name)}.h\"")
        else
            result.appendln("#include \"${Utils.createFileName(item.name)}.h\"")
        result.appendln()

        result.appendln("${Utils.createTypeName(item.name)} ${Utils.createVariableName(item.name, "data")};")
        result.appendln()

        // And finally the main function which executes the network
        result.appendln(generateMain())

        // And then return the code!
        return result.toString().trim()
    }

    /**
     * Generates a string that represents the main function that does all the logic required for this runnable file
     */
    private fun generateMain(): String {
        val result = StringBuilder()

        // Open the function
        result.appendln("int main(void) {")

        // Generate the initialisation code
        result.append(generateInitialisation())
        result.appendln()

        // Open the main loop (iterations depend on simulation time and step size)
        result.appendln("${config.getIndent(1)}unsigned int i = 0;")
        result.appendln("${config.getIndent(1)}for(i=1; i <= (SIMULATION_TIME / STEP_SIZE); i++) {")

        result.appendln("${config.getIndent(2)}${Utils.createFunctionName(item.name, "Run")}(&${Utils.createVariableName(item.name, "data")});")
        result.appendln()

        // Generate the logging code
        result.append(generateLogging())

        // Close the main loop
        result.appendln("${config.getIndent(1)}}")
        result.appendln()

        // Return from the function (0 is success)
        result.appendln("${config.getIndent(1)}return 0;")

        // Close the function
        result.appendln("}")

        // And return the main function
        return result.toString()
    }

    /**
     * Generates a string that initialises all of the variables used in the network. If the parametrisation method is
     * Run Time, then this method will also generate code that applies parameters to the objects.
     */
    private fun generateInitialisation(): String {
        val result = StringBuilder()

        if (config.parametrisationMethod == ParametrisationMethod.RUN_TIME) {
            // Firstly we want to call the default parametrisation for the model, in case we don't set any
            result.appendln("${config.getIndent(1)}${Utils.createFunctionName(item.name, "Parametrise")}(&${Utils.createVariableName(item.name, "data")});")
        }

        result.appendln("${config.getIndent(1)}${Utils.createFunctionName(item.name, "Init")}(&${Utils.createVariableName(item.name, "data")});")
        result.appendln()

        // Next we need to add some code that handles logging, if it is enabled
        // Let's start off by checking the guard for logging
        result.appendln("#if ENABLE_LOGGING")

        // If we are logging, then we would want to open the file pointer
        result.appendln("${config.getIndent(1)}FILE* fp = fopen(LOGGING_FILE, \"w\");")

        // And fill in the title row (the time, followed by each variable we're logging
        result.append("${config.getIndent(1)}fprintf(fp, \"Time")
        for ((variable, _) in toLog) {
            result.append(",${item.name}.$variable")
        }
        result.appendln("\\n\");")

        // Next we can fill in the first row (time 0) in the same order as the titles, here we use a format string to
        // correctly format the types we're logging
        result.append("${config.getIndent(1)}fprintf(fp, \"%f")
        for ((_, type) in toLog) {
            result.append(",${Utils.generatePrintfType(type)}")
        }
        result.append("\\n\", 0.0")
        for ((variable, _) in toLog) {
            result.append(", ${Utils.createVariableName(item.name, "data")}.${Utils.createVariableName(variable)}")
        }
        result.appendln(");")

        // We keep track of when we last logged, for the case where the logging interval is not the same as the step
        // size
        result.appendln("${config.getIndent(1)}unsigned int last_log = 0;")

        // And now we can close the check for the logging guard
        result.appendln("#endif")

        // And we're done for initialisation! Return the code
        return result.toString()
    }

    /**
     * Generates a string that logs (if applicable) the required fields to the pre-created file pointer
     */
    private fun generateLogging(): String {
        val result = StringBuilder()

        // Let's start the logging code
        result.appendln("${config.getIndent(2)}/* Logging */")

        // We would only run this code if logging is enabled
        result.appendln("#if ENABLE_LOGGING")

        // And only if we're up to the next logging time
        result.appendln("${config.getIndent(2)}if((i - last_log) >= LOGGING_INTERVAL / STEP_SIZE) {")

        // If all is good, then we log all the fields that we need to, using a format string to correctly format the
        // types we're logging
        result.append("${config.getIndent(3)}fprintf(fp, \"%f")
        for ((_, type) in toLog) {
            result.append(",${Utils.generatePrintfType(type)}")
        }
        result.append("\\n\", i*STEP_SIZE")
        for ((variable, _) in toLog) {
            result.append(", ${Utils.createVariableName(item.name, "data")}.${Utils.createVariableName(variable)}")
        }
        result.appendln(");")

        // Keep track of when the last log was written
        result.appendln("${config.getIndent(3)}last_log = i;")

        // Close the check for checking if we're up to the next logging time
        result.appendln("${config.getIndent(2)}}")

        // Close the check for the logging guard
        result.appendln("#endif")

        // And return the logging code
        return result.toString()
    }
}