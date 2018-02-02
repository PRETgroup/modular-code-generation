package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.ParametrisationMethod
import me.nallen.modularCodeGeneration.hybridAutomata.*

/**
 * The class that contains methods to do with the generation of Header Files for the Hybrid Automata
 */
object HFileGenerator {
    private var config: Configuration = Configuration()

    /**
     * Generates a string that represents the Header File for the given Hybrid Automata
     */
    fun generate(item: HybridItem, config: Configuration = Configuration()): String {
        this.config = config

        // Let's start creating the header file!
        val result = StringBuilder()

        // Generate the guard
        result.appendln("#ifndef ${Utils.createMacroName(item.name)}_H_")
        result.appendln("#define ${Utils.createMacroName(item.name)}_H_")
        result.appendln()

        // Add in any includes we need
        result.appendln(generateIncludes(item))

        if(item is HybridAutomata)
            // Create the enum for the states this automata uses
            result.appendln(generateEnum(item))

        // Create the struct that holds all variables for the automata
        result.appendln(generateStruct(item))

        // If we have run time parametrisation
        if(config.parametrisationMethod == ParametrisationMethod.RUN_TIME) {
            // Then we need to declare a parametrisation function
            result.appendln("// ${item.name} Default Parametrisation function")
            result.appendln("void ${Utils.createFunctionName(item.name, "Parametrise")}(${Utils.createTypeName(item.name)}* me);")
            result.appendln()
        }

        // Declare the initialisation function
        result.appendln("// ${item.name} Initialisation function")
        result.appendln("void ${Utils.createFunctionName(item.name, "Init")}(${Utils.createTypeName(item.name)}* me);")
        result.appendln()

        // Declare the execution function
        result.appendln("// ${item.name} Execution function")
        result.appendln("void ${Utils.createFunctionName(item.name, "Run")}(${Utils.createTypeName(item.name)}* me);")
        result.appendln()

        // Close the guard
        result.appendln("#endif // ${Utils.createMacroName(item.name)}_H_")

        return result.toString().trim()
    }

    /**
     * Generates a string that represents the list of includes that are needed for the automata
     */
    private fun generateIncludes(item: HybridItem): String {
        val result = StringBuilder()

        // Default set of includes that are always needed
        result.appendln("#include <stdint.h>")
        result.appendln("#include <stdlib.h>")
        result.appendln("#include <stdio.h>")
        result.appendln("#include <math.h>") // This may not be needed in all cases
        result.appendln()
        // TODO: Check if FSM uses a math.h function (sqrt, pow, etc.)

        if(item is HybridNetwork) {
            result.appendln(generateNetworkIncludes(item))
        }

        // Define the boolean type
        result.appendln("typedef int bool;")
        result.appendln("#define false 0")
        result.appendln("#define true 1")

        result.appendln()
        // Include the config file
        result.appendln("#include \"${CCodeGenerator.CONFIG_FILE}\"")

        if(item is HybridAutomata) {
            // If we have delayed variables
            if(item.variables.any({it.canBeDelayed()})) {
                // Include the delayable file
                result.appendln("#include \"${CCodeGenerator.DELAYABLE_HEADER}\"")
            }
        }

        // Return all the includes
        return result.toString()
    }

    /**
     * Generates a string that represents all the includes that are required for this runnable file
     */
    private fun generateNetworkIncludes(network: HybridNetwork): String {
        val result = StringBuilder()

        // Now we need to add an include statement for each type we have to add
        if(network.instances.isNotEmpty()) {
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
     * Generates a string that represents the enum of the possible locations that the automata can be in
     */
    private fun generateEnum(automata: HybridAutomata): String {
        val result = StringBuilder()

        // Declare the enum name (based on the automata name)
        result.appendln("// ${automata.name} States")
        result.appendln("enum ${Utils.createTypeName(automata.name, "States")} {")

        // Then, for each location
        for((name) in automata.locations) {
            // Create an entry in the num for it
            result.appendln("${config.getIndent(1)}${Utils.createMacroName(automata.name, name)},")
        }
        result.appendln("};")

        // Return the enum
        return result.toString()
    }

    /**
     * Generates a string that represents the struct that holds all the data about the automata, including state and
     * any variables
     */
    private fun generateStruct(item: HybridItem): String {
        val result = StringBuilder()

        // Start the struct declaration
        result.appendln("// ${item.name} Data Struct")
        result.appendln("typedef struct {")

        if(item is HybridAutomata) {
            result.append(generateAutomataStruct(item))
        }
        else if(item is HybridNetwork) {
            result.append(generateNetworkStruct(item))
        }

        // Close the struct declaration with the type name (based on the automata name)
        result.appendln("} ${Utils.createTypeName(item.name)};")

        return result.toString()
    }

    private fun generateAutomataStruct(automata: HybridAutomata): String {
        val result = StringBuilder()

        // We need to store the state
        result.appendln("${config.getIndent(1)}// State")
        result.appendln("${config.getIndent(1)}enum ${Utils.createTypeName(automata.name, "States")} state;")

        // We want to generate declarations for each of the different types of variables we might have
        // Inputs
        result.append(Utils.performVariableFunctionForLocality(automata, Locality.EXTERNAL_INPUT, HFileGenerator::generateVariableDeclaration, config, "Declare"))
        // Ouputs
        result.append(Utils.performVariableFunctionForLocality(automata, Locality.EXTERNAL_OUTPUT, HFileGenerator::generateVariableDeclaration, config, "Declare"))
        // Internals
        result.append(Utils.performVariableFunctionForLocality(automata, Locality.INTERNAL, HFileGenerator::generateVariableDeclaration, config, "Declare"))
        // Parameters
        result.append(Utils.performVariableFunctionForLocality(automata, Locality.PARAMETER, HFileGenerator::generateVariableDeclaration, config, "Declare"))

        // Finally, if we have any delayed variables
        if (automata.variables.any({ it.canBeDelayed() })) {
            // Then we want to declare those too (these are only internal)
            result.appendln()
            result.appendln("${config.getIndent(1)}// Declare Delayed Variables")

            // For each delayed variable
            for (variable in automata.variables.filter { it.canBeDelayed() }) {
                // Declare the delayed variant of the variable
                result.appendln("${config.getIndent(1)}${Utils.createTypeName("Delayable", Utils.generateCType(variable.type))} ${Utils.createVariableName(variable.name, "delayed")};")
            }
        }

        return result.toString()
    }

    private fun generateNetworkStruct(network: HybridNetwork): String {
        val result = StringBuilder()

        // Simply iterate over each object that we create
        for((name, instance) in network.instances) {
            // Different instantiated type depending on the parametrisation method
            if (config.parametrisationMethod == ParametrisationMethod.COMPILE_TIME) {
                // Compile time means the type is just itself
                result.appendln("${config.getIndent(1)}${Utils.createTypeName(name)} ${Utils.createVariableName(name, "data")};")
            } else {
                // Run time means that the type is the instance's definition type
                result.appendln("${config.getIndent(1)}${Utils.createTypeName(instance.automata)} ${Utils.createVariableName(name, "data")};")
            }
        }

        return result.toString()
    }

    /**
     * Generates a string that represents a variable declaration
     */
    private fun generateVariableDeclaration(variable: Variable): String {
        // Simply just the type (converted to C type) and the variable name (conforming to style)
        return "${Utils.generateCType(variable.type)} ${Utils.createVariableName(variable.name)};"
    }
}