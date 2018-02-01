package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.CodeGenManager
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.LoggingField
import me.nallen.modularCodeGeneration.codeGen.ParametrisationMethod
import me.nallen.modularCodeGeneration.hybridAutomata.HybridNetwork

/**
 * The class that contains methods to do with the generation of the main runnable file for the system
 */
object RunnableGenerator {
    private var network: HybridNetwork = HybridNetwork()
    private var config: Configuration = Configuration()

    private var requireSelfReferenceInFunctionCalls: Boolean = false
    private var objects: ArrayList<CodeObject> = ArrayList()
    private var toLog: List<LoggingField> = ArrayList()

    /**
     * Generates a string that represents the main runnable file for the system.
     */
    fun generate(network: HybridNetwork, config: Configuration = Configuration()): String {
        this.network = network
        this.config = config

        // Whether or not we need to include self references in custom functions
        this.requireSelfReferenceInFunctionCalls = config.parametrisationMethod == ParametrisationMethod.RUN_TIME

        // We need to get a list of all objects we need to instantiate, and what type they should be
        objects.clear()
        for((name, instance) in network.instances) {
            // Different instantiated type depending on the parametrisation method
            if(config.parametrisationMethod == ParametrisationMethod.COMPILE_TIME) {
                // Compile time means the type is just itself
                objects.add(CodeObject(name, name))
            }
            else {
                // Run time means that the type is the instance's definition type
                objects.add(CodeObject(name, instance.automata))
            }
        }

        // Collect all the fields that we will need to log (if any)
        toLog = CodeGenManager.collectFieldsToLog(network, config)

        // Now let's build the runnable
        val result = StringBuilder()

        // Start with all the includes at the top of the file
        result.appendln(generateIncludes())

        // Then the declaration of all the variables
        result.appendln(generateVariables())

        // And finally the main function which executes the network
        result.appendln(generateMain())

        // And then return the code!
        return result.toString().trim()
    }

    /**
     * Generates a string that represents all the includes that are required for this runnable file
     */
    private fun generateIncludes(): String {
        val result = StringBuilder()

        // A standard set of includes are required regardless
        result.appendln("#include <stdint.h>")
        result.appendln("#include <stdlib.h>")
        result.appendln("#include <stdio.h>")
        result.appendln("#include <string.h>")

        // Now we need to add an include statement for each type we have to add
        if(network.instances.isNotEmpty()) {
            result.appendln()

            // Different logic depending on the parametrisation method
            if(config.parametrisationMethod == ParametrisationMethod.COMPILE_TIME) {
                // If it's compile time then we need to include a type for each instantiation
                for((name, instance) in network.instances) {
                    // And the includes will be in a sub-directory of the instance type
                    val subfolder = if(instance.automata.equals(network.name, true)) { instance.automata + " Files" } else { instance.automata }
                    result.appendln("#include \"${Utils.createFolderName(subfolder)}/${Utils.createFileName(name)}.h\"")
                }
            }
            else {
                // Otherwise it's run time which means we only need to include once per definition type

                // So keep track of which types we've handled
                val generated = ArrayList<String>()
                for((_, instance) in network.instances) {
                    // Check if we've seen this type before
                    if (!generated.contains(instance.automata)) {
                        // If we haven't seen it, keep track of it
                        generated.add(instance.automata)

                        // And add the include
                        result.appendln("#include \"${Utils.createFileName(instance.automata)}.h\"")
                    }
                }
            }
        }

        // Return all the includes
        return result.toString()
    }

    /**
     * Generates a string that represents the declaration of all the variables that are required for this runnable file
     */
    private fun generateVariables(): String {
        val result = StringBuilder()

        // Simply iterate over each object that we created earlier
        for((name, instance) in objects) {
            // And add the type and variable name
            result.appendln("${Utils.createTypeName(instance)} ${Utils.createVariableName(name, "data")};")
        }

        // The return all the variables
        return result.toString()
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

        // Generate the I/O Mappings
        result.append(generateIOMappings())

        result.appendln()
        result.appendln()

        // Generate the code that runs all of the machines
        result.append(generateRunSection())

        result.appendln()
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

        // Let's start the initialisation code
        result.appendln("${config.getIndent(1)}/* Initialise Structs */")
        // We need to initialise every object
        var first = true
        for ((name, instance) in objects) {
            if (!first)
                result.appendln()
            first = false

            // Call a memset for the variable to allocate the memory and actually create the object
            result.appendln("${config.getIndent(1)}(void) memset((void *)&${Utils.createVariableName(name, "data")}, 0, sizeof(${Utils.createTypeName(instance)}));")

            // Now, if it's run-time parametrisation then we need to do some extra logic
            if (config.parametrisationMethod == ParametrisationMethod.RUN_TIME) {
                // Firstly we want to call the default parametrisation for the model, in case we don't set any
                result.appendln("${config.getIndent(1)}${Utils.createFunctionName(instance, "Parametrise")}(&${Utils.createVariableName(name, "data")});")

                // Next we need to go through every parameter that we need to set
                for ((key, value) in network.instances[name]!!.parameters) {
                    // And set that parameter value accordingly
                    result.appendln("${config.getIndent(1)}${Utils.createVariableName(name, "data")}.${Utils.createVariableName(key)} = ${Utils.generateCodeForParseTreeItem(value, Utils.PrefixData("${Utils.createVariableName(name, "data")}.", requireSelfReferenceInFunctionCalls))};")
                }
            }

            // Finally, we can call the initialisation function for the object
            result.appendln("${config.getIndent(1)}${Utils.createFunctionName(instance, "Init")}(&${Utils.createVariableName(name, "data")});")
        }

        result.appendln()

        // Next we need to add some code that handles logging, if it is enabled
        // Let's start off by checking the guard for logging
        result.appendln("#if ENABLE_LOGGING")

        // If we are logging, then we would want to open the file pointer
        result.appendln("${config.getIndent(1)}FILE* fp = fopen(LOGGING_FILE, \"w\");")

        // And fill in the title row (the time, followed by each variable we're logging
        result.append("${config.getIndent(1)}fprintf(fp, \"Time")
        for ((machine, variable, _) in toLog) {
            result.append(",$machine.$variable")
        }
        result.appendln("\\n\");")

        // Next we can fill in the first row (time 0) in the same order as the titles, here we use a format string to
        // correctly format the types we're logging
        result.append("${config.getIndent(1)}fprintf(fp, \"%f")
        for ((_, _, type) in toLog) {
            result.append(",${Utils.generatePrintfType(type)}")
        }
        result.append("\\n\", 0.0")
        for ((machine, variable, _) in toLog) {
            result.append(", ${Utils.createVariableName(machine, "data")}.${Utils.createVariableName(variable)}")
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
     * Generates a string that handles all of the mapping between automata inputs and outputs
     */
    private fun generateIOMappings(): String {
        val result = StringBuilder()

        // Let's start the mapping code
        result.appendln("${config.getIndent(2)}/* Mappings */")

        // Get a list of the inputs that we're assigning to, in a sorted order so it looks slightly nicer
        val keys = network.ioMapping.keys.sortedWith(compareBy({ it.automata }, { it.variable }))

        // And get a map of all the instance names that we need to use here (they're formatted differently to variables)
        val customVariableNames = network.instances.mapValues({ Utils.createVariableName(it.key, "data") })

        // Now we go through each input
        var prev = ""
        for (key in keys) {
            if (prev != "" && prev != key.automata)
                result.appendln()
            prev = key.automata

            // And assign to the input, the correct output (or combination of them)
            val from = network.ioMapping[key]!!
            result.appendln("${config.getIndent(2)}${Utils.createVariableName(key.automata, "data")}.${Utils.createVariableName(key.variable)} = ${Utils.generateCodeForParseTreeItem(from, Utils.PrefixData("", requireSelfReferenceInFunctionCalls, customVariableNames = customVariableNames))};")
        }

        // Done, return all the mappings
        return result.toString()
    }

    /**
     * Generates a string that executes all of the automata in the network
     */
    private fun generateRunSection(): String {
        val result = StringBuilder()

        // Let's start the run code
        result.appendln("${config.getIndent(2)}/* Run Automata */")

        // We go through each instance we've created
        var first = true
        for ((name, instance) in objects) {
            if (!first)
                result.appendln()
            first = false

            // And simply call the "Run" function for it
            result.appendln("${config.getIndent(2)}${Utils.createFunctionName(instance, "Run")}(&${Utils.createVariableName(name, "data")});")
        }

        // And return the collection of "Run" functions
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
        for ((_, _, type) in toLog) {
            result.append(",${Utils.generatePrintfType(type)}")
        }
        result.append("\\n\", i*STEP_SIZE")
        for ((machine, variable, _) in toLog) {
            result.append(", ${Utils.createVariableName(machine, "data")}.${Utils.createVariableName(variable)}")
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

    /**
     * A class that captures an "object" (variable) in the code which has a name and a type
     */
    private data class CodeObject(
            // The object / variable name
            val name: String,

            // The object / variable type
            val type: String
    )
}