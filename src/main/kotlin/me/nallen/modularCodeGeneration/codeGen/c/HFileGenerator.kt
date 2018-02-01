package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.ParametrisationMethod
import me.nallen.modularCodeGeneration.hybridAutomata.HybridAutomata
import me.nallen.modularCodeGeneration.hybridAutomata.Locality
import me.nallen.modularCodeGeneration.hybridAutomata.Variable

/**
 * The class that contains methods to do with the generation of Header Files for the Hybrid Automata
 */
object HFileGenerator {
    private var automata: HybridAutomata = HybridAutomata("Temp")
    private var config: Configuration = Configuration()

    /**
     * Generates a string that represents the Header File for the given Hybrid Automata
     */
    fun generate(automata: HybridAutomata, config: Configuration = Configuration()): String {
        this.automata = automata
        this.config = config

        // Let's start creating the header file!
        val result = StringBuilder()

        // Generate the guard
        result.appendln("#ifndef ${Utils.createMacroName(automata.name)}_H_")
        result.appendln("#define ${Utils.createMacroName(automata.name)}_H_")
        result.appendln()

        // Add in any includes we need
        result.appendln(generateIncludes())

        // Create the enum for the states this automata uses
        result.appendln(generateEnum())

        // Create the struct that holds all variables for the automata
        result.appendln(generateStruct())

        // If we have run time parametrisation
        if(config.parametrisationMethod == ParametrisationMethod.RUN_TIME) {
            // Then we need to declare a parametrisation function
            result.appendln("// ${automata.name} Default Parametrisation function")
            result.appendln("void ${Utils.createFunctionName(automata.name, "Parametrise")}(${Utils.createTypeName(automata.name)}* me);")
            result.appendln()
        }

        // Declare the initialisation function
        result.appendln("// ${automata.name} Initialisation function")
        result.appendln("void ${Utils.createFunctionName(automata.name, "Init")}(${Utils.createTypeName(automata.name)}* me);")
        result.appendln()

        // Declare the execution function
        result.appendln("// ${automata.name} Execution function")
        result.appendln("void ${Utils.createFunctionName(automata.name, "Run")}(${Utils.createTypeName(automata.name)}* me);")
        result.appendln()

        // Close the guard
        result.appendln("#endif // ${Utils.createMacroName(automata.name)}_H_")

        return result.toString().trim()
    }

    /**
     * Generates a string that represents the list of includes that are needed for the automata
     */
    private fun generateIncludes(): String {
        val result = StringBuilder()

        // Default set of includes that are always needed
        result.appendln("#include <stdint.h>")
        result.appendln("#include <stdlib.h>")
        result.appendln("#include <stdio.h>")
        result.appendln("#include <math.h>") // This may not be needed in all cases
        // TODO: Check if FSM uses a math.h function (sqrt, pow, etc.)

        // Define the boolean type
        result.appendln()
        result.appendln("typedef int bool;")
        result.appendln("#define false 0")
        result.appendln("#define true 1")

        result.appendln()
        // Include the config file, which will differ for depending on if we've done compile time parametrisation
        if(config.parametrisationMethod == ParametrisationMethod.COMPILE_TIME)
            result.appendln("#include \"../${CCodeGenerator.CONFIG_FILE}\"")
        else
            result.appendln("#include \"${CCodeGenerator.CONFIG_FILE}\"")

        // If we have delayed variables
        if(automata.variables.any({it.canBeDelayed()})) {
            // Include the delayable file, which will differ for depending on if we've done compile time parametrisation
            if(config.parametrisationMethod == ParametrisationMethod.COMPILE_TIME)
                result.appendln("#include \"../${CCodeGenerator.DELAYABLE_HEADER}\"")
            else
                result.appendln("#include \"${CCodeGenerator.DELAYABLE_HEADER}\"")
        }

        // Return all the includes
        return result.toString()
    }

    /**
     * Generates a string that represents the enum of the possible locations that the automata can be in
     */
    private fun generateEnum(): String {
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
    private fun generateStruct(): String {
        val result = StringBuilder()

        // Start the struct declaration
        result.appendln("// ${automata.name} Data Struct")
        result.appendln("typedef struct {")

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
        if(automata.variables.any({it.canBeDelayed()})) {
            // Then we want to declare those too (these are only internal)
            result.appendln()
            result.appendln("${config.getIndent(1)}// Declare Delayed Variables")

            // For each delayed variable
            for(variable in automata.variables.filter{it.canBeDelayed()}) {
                // Declare the delayed variant of the variable
                result.appendln("${config.getIndent(1)}${Utils.createTypeName("Delayable", Utils.generateCType(variable.type))} ${Utils.createVariableName(variable.name, "delayed")};")
            }
        }

        // Close the struct declaration with the type name (based on the automata name)
        result.appendln("} ${Utils.createTypeName(automata.name)};")

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