package me.nallen.modularcodegeneration.codegen.c

import me.nallen.modularcodegeneration.codegen.CodeGenManager
import me.nallen.modularcodegeneration.codegen.Configuration
import me.nallen.modularcodegeneration.codegen.ParametrisationMethod
import me.nallen.modularcodegeneration.codegen.SaturationDirection
import me.nallen.modularcodegeneration.hybridautomata.*
import me.nallen.modularcodegeneration.parsetree.Literal
import me.nallen.modularcodegeneration.parsetree.Multiply
import me.nallen.modularcodegeneration.parsetree.Plus
import me.nallen.modularcodegeneration.parsetree.VariableType

import me.nallen.modularcodegeneration.parsetree.Variable as ParseTreeVariable

/**
 * The class that contains methods to do with the generation of Source Files for the Hybrid Item
 */
object CFileGenerator {
    private var config: Configuration = Configuration()

    private var requireSelfReferenceInFunctionCalls: Boolean = false
    private val delayedVariableTypes: HashMap<String, VariableType> = HashMap()

    private var automata: HybridItem = HybridAutomata()
    private var objects: ArrayList<CodeObject> = ArrayList()

    /**
     * Generates a string that represents the Source File for the given Hybrid Item
     */
    fun generate(item: HybridItem, config: Configuration = Configuration()): String {
        this.config = config
        automata = item

        // If we're generating code for a Hybrid Network
        if(item is HybridNetwork) {
            // We need to get a list of all objects we need to instantiate, and what type they should be
            objects.clear()
            // Go through each instance
            for((name, instance) in item.instances) {
                // And add the object we need to create
                val instantiate = item.getInstantiateForInstantiateId(instance.instantiate)
                if(instantiate != null) {
                    objects.add(CodeObject(name, instantiate.name))
                }
            }
        }

        // Whether or not we need to include self references in custom functions
        this.requireSelfReferenceInFunctionCalls = config.parametrisationMethod == ParametrisationMethod.RUN_TIME

        // Create a list of the variables that can be delayed, and the types that they represent
        this.delayedVariableTypes.clear()
        for(variable in item.variables.filter {it.canBeDelayed()})
            this.delayedVariableTypes[variable.name] = variable.type

        // Now let's build the source file
        val result = StringBuilder()

        // First off, we'll include the associated header file
        result.appendln("#include \"${Utils.createFileName(item.name)}.h\"")
        result.appendln()

        // If this is an Automata, then we also want to add the custom functions
        if(item is HybridAutomata) {
            //Generate the code for any custom functions, if any
            if(item.functions.size > 0)
                result.appendln(generateCustomFunctions(item))
        }

        // If we're supporting run time parametrisation then we want to generate the default parametrisation function
        if(config.parametrisationMethod == ParametrisationMethod.RUN_TIME)
            result.appendln(generateParametrisationFunction(item))

        // Generate the initialisation function
        result.appendln(generateInitialisationFunction(item))

        // Generate the execution / step function
        result.appendln(generateExecutionFunction(item))

        // And return the total source file contents
        return result.toString().trim()
    }

    /**
     * Generates a string that captures all custom functions defined in this automata, including both the method
     * signatures and the method bodies
     */
    private fun generateCustomFunctions(automata: HybridAutomata): String {
        val result = StringBuilder()

        // Iterate over every function in the automaton
        for(function in automata.functions) {
            // Now we declare the method signature for the function
            // Note that it is declared as "static" so that there are no conflicts with other automata
            result.append("static ${Utils.generateCType(function.returnType)} ${Utils.createFunctionName(function.name)}(")
            // Now we need to add all the parameters
            var first = true
            // If we require self references then the first parameter needs to be as such
            if(requireSelfReferenceInFunctionCalls) {
                first = false
                result.append("${Utils.createTypeName(automata.name)}* me")
            }
            // And for every input to the function we create a parameter
            for(input in function.inputs) {
                if(!first)
                    result.append(", ")
                first = false

                // Generate both the type and variable name
                result.append("${Utils.generateCType(input.type)} ${Utils.createVariableName(input.name)}")
            }
            // Open the function
            result.appendln(") {")

            // We need to set all parameters to use the value stored inside the self reference, as this is where their
            // values will be stored
            val customDefinedVariables = LinkedHashMap<String, String>(Utils.DEFAULT_CUSTOM_VARIABLES)
            for(parameter in automata.variables.filter {it.locality == Locality.PARAMETER}) {
                customDefinedVariables[parameter.name] = "me->${Utils.createVariableName(parameter.name)}"
            }

            // Now we can generate the code for the internal body of the function!
            result.appendln(Utils.generateCodeForProgram(function.logic, config, 1, Utils.PrefixData("", requireSelfReferenceInFunctionCalls, delayedVariableTypes, customDefinedVariables)))

            // Close the function
            result.appendln("}")
            result.appendln()
        }

        // Return all the functions
        return result.toString()
    }

    /**
     * Generates a string that captures the default parametrisation of the item
     */
    private fun generateParametrisationFunction(item: HybridItem): String {
        val result = StringBuilder()

        // Let's start the default parametrisation function
        result.appendln("// ${item.name} Default Parametrisation function")

        // Create the method name
        result.appendln("void ${Utils.createFunctionName(item.name, "Parametrise")}(${Utils.createTypeName(item.name)}* me) {")

        // We only need to add any logic here if there are actually any parameters with default values
        if(item.variables.any {it.locality == Locality.PARAMETER && it.defaultValue != null})
        // There's at least one, so let's add the code
            result.append(Utils.performVariableFunctionForLocality(item, Locality.PARAMETER, CFileGenerator::generateParameterInitialisation, config, "Initialise Default"))

        // If the item we're generating for is a Network
        if(item is HybridNetwork) {
            // We need to initialise every object
            var first = true
            for ((name, instance) in objects) {
                if (!first)
                    result.appendln()
                first = false

                // Now, if it's run-time parametrisation then we need to do some extra logic
                if (config.parametrisationMethod == ParametrisationMethod.RUN_TIME) {
                    // Firstly we want to call the default parametrisation for the model, in case we don't set any
                    result.appendln("${config.getIndent(1)}${Utils.createFunctionName(instance, "Parametrise")}(&me->${Utils.createVariableName(name, "data")});")

                    // Next we need to go through every parameter that we need to set
                    for ((key, value) in item.instances[name]!!.parameters) {
                        // And set that parameter value accordingly
                        result.appendln("${config.getIndent(1)}me->${Utils.createVariableName(name, "data")}.${Utils.createVariableName(key)} = ${Utils.generateCodeForParseTreeItem(value, Utils.PrefixData("${Utils.createVariableName(name, "data")}.", requireSelfReferenceInFunctionCalls))};")
                    }
                }
            }
        }

        // Close the method
        result.appendln("}")

        // And return the parametrisation method
        return result.toString()
    }

    /**
     * Generates a string that captures the initialisation of the item, including state and variables (if applicable)
     */
    private fun generateInitialisationFunction(item: HybridItem): String {
        val result = StringBuilder()

        // Let's start the initialisation function
        result.appendln("// ${item.name} Initialisation function")

        // Create the method, which just takes in a self reference
        result.appendln("void ${Utils.createFunctionName(item.name, "Init")}(${Utils.createTypeName(item.name)}* me) {")

        // If it's an Automata then we have some extra logic to include
        if(item is HybridAutomata)
            result.append(generateAutomataIntialisationFunction(item))

        // If it's a Network then we have some extra logic to include
        if(item is HybridNetwork) {
            // We need to initialise every object

            if(objects.size > 0)
                result.appendln("${config.getIndent(1)}// Initialise Sub-objects")
            var first = true
            for ((name, instance) in objects) {
                if (!first)
                    result.appendln()
                first = false

                result.appendln("${config.getIndent(1)}${Utils.createFunctionName(instance, "Init")}(&me->${Utils.createVariableName(name, "data")});")
            }
        }

        // Now we want to initialise all outputs from the automaton
        result.append(Utils.performVariableFunctionForLocality(item, Locality.EXTERNAL_OUTPUT, CFileGenerator::generateVariableInitialisation, config, "Initialise", blankStart = true, blankEnd = false))

        // As well as any internal variables
        result.append(Utils.performVariableFunctionForLocality(item, Locality.INTERNAL, CFileGenerator::generateVariableInitialisation, config, "Initialise", blankStart = true, blankEnd = false))

        // Now we need to look for delayed variables to initialise too
        if (item.variables.any { it.canBeDelayed() }) {
            result.appendln()
            result.append("${config.getIndent(1)}// Initialise Delayed Variables")

            // Iterate over every variable that needs to be delayed
            for (variable in item.variables
                    .filter { it.canBeDelayed() }) {
                // For delayed variables, we need to call a memset to create the structure that holds the delayed data,
                // as well as initialise the delayable structure
                result.appendln()
                result.appendln("${config.getIndent(1)}(void) memset((void *)&me->${Utils.createVariableName(variable.name, "delayed")}, 0, sizeof(${Utils.createTypeName("Delayable", Utils.generateCType(variable.type))}));")
                result.appendln("${config.getIndent(1)}${Utils.createFunctionName("Delayable", Utils.generateCType(variable.type), "Init")}(&me->${Utils.createVariableName(variable.name, "delayed")}, ${Utils.generateCodeForParseTreeItem(variable.delayableBy!!, Utils.PrefixData("me->"))});")
            }
        }



        // Close the method
        result.appendln("}")

        // Return the initialisation code
        return result.toString()
    }

    /**
     * Generates initialisation code that's specific to Hybrid Automata, such as the state
     */
    private fun generateAutomataIntialisationFunction(automata: HybridAutomata): String {
        val result = StringBuilder()

        // First off, let's initialise the state correctly
        result.appendln("${config.getIndent(1)}// Initialise State")
        result.appendln("${config.getIndent(1)}me->state = ${Utils.createMacroName(automata.name, automata.init.state)};")

        return result.toString()
    }

    /**
     * Generates a string that initialises a given Variable
     */
    private fun generateVariableInitialisation(variable: Variable): String {
        // Let's start with a default value for the initialisation
        var initValue: String = generateDefaultInitForType(variable.type)

        // But, if an initial value for the variable is provided then let's use that
        if(automata is HybridAutomata && (automata as HybridAutomata).init.valuations.containsKey(variable.name)) {
            // Generate code that represents the initial value for the variable
            initValue = Utils.generateCodeForParseTreeItem((automata as HybridAutomata).init.valuations[variable.name] !!, Utils.PrefixData("me->", requireSelfReferenceInFunctionCalls, delayedVariableTypes))
        }
        else if(automata is HybridNetwork && (automata as HybridNetwork).ioMapping.containsKey(AutomataVariablePair("", variable.name))) {
            val customVariableNames = (automata as HybridNetwork).instances.mapValues { "me->${ Utils.createVariableName(it.key, "data")}" }
            initValue = Utils.generateCodeForParseTreeItem((automata as HybridNetwork).ioMapping[AutomataVariablePair("", variable.name)] !!, Utils.PrefixData("me->", requireSelfReferenceInFunctionCalls, delayedVariableTypes, customVariableNames))
        }

        // Now we can return the code that initialises the variable
        return "me->${Utils.createVariableName(variable.name)} = $initValue;"
    }

    /**
     * Generates the default value for given VariableType
     */
    private fun generateDefaultInitForType(type: VariableType): String {
        // A simple switch based on the type returns the default value for the types of variables
        return when(type) {
            VariableType.BOOLEAN -> Utils.generateCodeForParseTreeItem(Literal("false"))
            VariableType.REAL -> Utils.generateCodeForParseTreeItem(Literal("0"))
            VariableType.INTEGER -> Utils.generateCodeForParseTreeItem(Literal("0"))
            else -> throw NotImplementedError("Unable to generate code for requested type '$type'")
        }
    }

    /**
     * Generates a string that initialises a parameter to its default value
     */
    private fun generateParameterInitialisation(variable: Variable): String {
        // Check that we do actually have a default value
        if(variable.defaultValue != null) {
            // Now we can se the parameter to its default value
            return "me->${Utils.createVariableName(variable.name)} = ${Utils.generateCodeForParseTreeItem(variable.defaultValue!!, Utils.PrefixData("me->", requireSelfReferenceInFunctionCalls, delayedVariableTypes))};"
        }

        // Otherwise no default value, so let's skip this
        return ""
    }

    /**
     * Generates a string that captures the execution function of the automaton
     */
    private fun generateExecutionFunction(item: HybridItem): String {
        val result = StringBuilder()

        // Let's start the execution function
        result.appendln("// ${item.name} Execution function")

        // It's simply a function that takes a self reference as the only argument
        result.appendln("void ${Utils.createFunctionName(item.name, "Run")}(${Utils.createTypeName(item.name)}* me) {")

        // If it's an Automata then we have some extra logic to include
        if(item is HybridAutomata)
            result.append(generateAutomataExecutionFunction(item))

        // If it's a Network then we have some extra logic to include
        if(item is HybridNetwork)
            result.append(generateNetworkExecutionFunction(item))

        // Close the execution function
        result.appendln("}")

        // And return it!
        return result.toString()
    }

    /**
     * Generates execution code that's specific to Hybrid Automata
     */
    private fun generateAutomataExecutionFunction(automata: HybridAutomata): String {
        val result = StringBuilder()

        // We create intermediary variables for anything that we change - namely the state, outputs, and internals.
        // This is so that scheduling order of flow or update statements does not matter
        result.appendln("${config.getIndent(1)}// Create intermediary variables")
        result.appendln("${config.getIndent(1)}enum ${Utils.createTypeName(automata.name, "States")} state_u = me->state;")

        result.append(Utils.performVariableFunctionForLocality(automata, Locality.EXTERNAL_OUTPUT, CFileGenerator::generateIntermediateVariable, config))

        result.append(Utils.performVariableFunctionForLocality(automata, Locality.INTERNAL, CFileGenerator::generateIntermediateVariable, config))
        result.appendln()

        // Let's start by adding an entry to each delayed variable that we have to keep track of
        if (automata.variables.any { it.canBeDelayed() }) {
            for (variable in automata.variables.filter { it.canBeDelayed() })
                result.appendln("${config.getIndent(1)}${Utils.createFunctionName("Delayable", Utils.generateCType(variable.type), "Add")}(&me->${Utils.createVariableName(variable.name, "delayed")}, me->${Utils.createVariableName(variable.name)});")
            result.appendln()
        }

        // A few different semantics are supported in the configuration settings, which are handled by the following
        // code. Essentially, in some cases we may perform multiple Inter-location transitions per tick, so we need to
        // count how many have passed.
        val needsTransitionCounting = config.maximumInterTransitions > 1
        val defaultIndent = if (needsTransitionCounting) 2 else 1

        // If we could do more than 1 inter-location transition
        if (needsTransitionCounting) {
            // We want to make sure we only ever do that many at maximum through a while loop
            result.appendln("${config.getIndent(1)}unsigned int remaining_transitions = ${config.maximumInterTransitions};")
            result.appendln("${config.getIndent(1)}while(remaining_transitions > 0) {")
            if(config.ccodeSettings.hasLoopAnnotations) {
                result.appendln("${config.getIndent(2)}${config.ccodeSettings.getLoopAnnotation(config.maximumInterTransitions)}")
            }
            // As soon as we start the loop, we decrease the counter by one
            result.appendln("${config.getIndent(2)}// Decrement the remaining transitions available")
            result.appendln("${config.getIndent(2)}remaining_transitions--;")
            result.appendln()
        }

        // Now we can add in the state machine logic
        result.appendln(generateStateMachine(automata, needsTransitionCounting))

        // Now we do the opposite of earlier, and update each internal variable / output from the intermediary ones we
        // created and used throughout the state machine. Again we do it for state, outputs, and internals
        result.appendln("${config.getIndent(defaultIndent)}// Update from intermediary variables")
        result.appendln("${config.getIndent(defaultIndent)}me->state = state_u;")

        result.append(Utils.performVariableFunctionForLocality(automata, Locality.EXTERNAL_OUTPUT, CFileGenerator::updateFromIntermediateVariable, config, depth = defaultIndent))

        result.append(Utils.performVariableFunctionForLocality(automata, Locality.INTERNAL, CFileGenerator::updateFromIntermediateVariable, config, depth = defaultIndent))

        // If we could have done more than 1 inter-location transition
        if (needsTransitionCounting) {
            // We want to close the while loop we opened earlier
            result.appendln("${config.getIndent(1)}}")
        }

        // Similarly, another config option is to always require one intra-location transition per tick (i.e. one set of
        // flow constraints is always run)
        if (config.requireOneIntraTransitionPerTick) {
            // If this is the case, then we generate a special state machine that doesn't advance the state, but merely
            // performs the internal actions of the current state
            result.appendln()

            result.append(generateIntraStateMachine(automata))
        }

        return result.toString()
    }

    /**
     * Generates a string that captures an implementation of the state machine of the automaton.
     * Depending on the config settings, this implementation could include both inter-location and intra-location
     * transitions, or only inter-location transitions.
     */
    private fun generateStateMachine(automata: HybridAutomata, countTransitions: Boolean): String {
        val result = StringBuilder()

        // Keep a record of the indentation so that the code looks nice
        val defaultIndent = if(countTransitions) 2 else 1

        result.appendln("${config.getIndent(defaultIndent)}// Run the state machine for transition logic")
        if(config.requireOneIntraTransitionPerTick)
            result.appendln("${config.getIndent(defaultIndent)}// This will only be inter-location transitions, with intra-location transitions happening later")

        // Start the switch statement (operating on the current state) that captures the state machine
        result.appendln("${config.getIndent(defaultIndent)}switch(me->state) {")

        // For each location we will generate an entry
        for(location in automata.locations) {
            // Generate the case that matches this state
            result.appendln("${config.getIndent(defaultIndent+1)}case ${Utils.createMacroName(automata.name, location.name)}: // Logic for state ${location.name}")

            // For each transition that leaves this location, we need to check if it can be taken
            var atLeastOneIf = false
            for((_, toLocation, guard, update) in automata.edges.filter{it.fromLocation == location.name }) {
                // Check if the guard of the transition is satisfied
                result.appendln("${config.getIndent(defaultIndent+2)}${if(atLeastOneIf) { "else " } else { "" }}if(${Utils.generateCodeForParseTreeItem(guard, Utils.PrefixData("me->", requireSelfReferenceInFunctionCalls, delayedVariableTypes))}) {")

                // If it is satisfied, then we will perform the updates associated with the transition
                for((variable, equation) in update) {
                    result.appendln("${config.getIndent(defaultIndent+3)}${Utils.createVariableName(variable)}_u = ${Utils.generateCodeForParseTreeItem(equation, Utils.PrefixData("me->", requireSelfReferenceInFunctionCalls, delayedVariableTypes))};")
                }

                if(update.isNotEmpty())
                    result.appendln()

                // And update the state to correspond to where the transition will take us to
                result.appendln("${config.getIndent(defaultIndent+3)}// Next state is $toLocation")
                result.appendln("${config.getIndent(defaultIndent+3)}state_u = ${Utils.createMacroName(automata.name, toLocation)};")

                // Close the check for the guard
                result.appendln("${config.getIndent(defaultIndent+2)}}")

                atLeastOneIf = true
            }

            // Check if we should include intra-location transitions in this machine
            if(!config.requireOneIntraTransitionPerTick) {
                // Yes we should include them!
                // So let's start off with checking the invariant of the location
                result.appendln("${config.getIndent(defaultIndent+2)}${if(atLeastOneIf) { "else " } else { "" }}if(${Utils.generateCodeForParseTreeItem(location.invariant, Utils.PrefixData("me->", requireSelfReferenceInFunctionCalls, delayedVariableTypes))}) {")

                // And add the code for the intra-transition, which will include both flow and updates
                result.append(generateCodeForIntraLogic(location, automata, defaultIndent+3))

                // For an intra-location, the state won't change, so we can set it to itself
                result.appendln("${config.getIndent(defaultIndent+3)}// Remain in this state")
                result.appendln("${config.getIndent(defaultIndent+3)}state_u = ${Utils.createMacroName(automata.name, location.name)};")

                // We then also need to check if we're counting transitions
                if(countTransitions) {
                    // Performing an intra-location transition stops execution in this tick, so we need to do that
                    result.appendln()
                    result.appendln("${config.getIndent(defaultIndent+3)}// Taking an intra-location transition stops execution")
                    result.appendln("${config.getIndent(defaultIndent+3)}remaining_transitions = 0;")
                }

                // Close the invariant check
                result.appendln("${config.getIndent(defaultIndent+2)}}")

                atLeastOneIf = true
            }

            // If we're counting transitions
            if(atLeastOneIf && countTransitions) {
                // Then we want to add a check in case none of the transitions could be taken
                result.appendln("${config.getIndent(defaultIndent+2)}else {")
                // In this case, we will take no further transitions in this tick
                result.appendln("${config.getIndent(defaultIndent+3)}// No available transition stops execution")
                result.appendln("${config.getIndent(defaultIndent+3)}remaining_transitions = 0;")
                result.appendln("${config.getIndent(defaultIndent+2)}}")
            }

            // Break from the state machine logic for this state
            result.appendln("${config.getIndent(defaultIndent+2)}break;")
        }

        // Close the switch statement
        result.appendln("${config.getIndent(defaultIndent)}}")

        // Return the state machine
        return result.toString()
    }

    /**
     * Generates a string that captures only the intra-location transitions of the automaton.
     * Note that this code will not modify the state variable, only read it
     */
    private fun generateIntraStateMachine(automata: HybridAutomata): String {
        val result = StringBuilder()

        // Start the state machine
        result.appendln("${config.getIndent(1)}// Intra-location logic for every state")

        // Start the switch statement (operating on the current state) that captures the state machine
        result.appendln("${config.getIndent(1)}switch(me->state) {")

        // For each location we will generate an entry
        for(location in automata.locations) {
            // Generate the case that matches this state
            result.appendln("${config.getIndent(2)}case ${Utils.createMacroName(automata.name, location.name)}: // Intra-location logic for state ${location.name}")

            // Add the code for the intra-transition, which will include both flow and updates
            result.append(generateCodeForIntraLogic(location, automata, 3))

            // Break from the state machine logic for this state
            result.appendln("${config.getIndent(3)}break;")
        }

        // Close the switch statement
        result.appendln("${config.getIndent(1)}}")
        result.appendln()

        // Now we do the opposite of earlier, and update each internal variable / output from the intermediary ones we
        // created and used throughout the state machine. This time however, we don't need to worry about state as that
        // won't have changed
        result.appendln("${config.getIndent(1)}// Update from intermediary variables")
        result.append(Utils.performVariableFunctionForLocality(automata, Locality.EXTERNAL_OUTPUT, CFileGenerator::updateFromIntermediateVariable, config))

        result.append(Utils.performVariableFunctionForLocality(automata, Locality.INTERNAL, CFileGenerator::updateFromIntermediateVariable, config))

        // Return the state machine
        return result.toString()
    }

    /**
     * Generates code that captures the intra-location logic for a given Location.
     * This will include both the flow (ODEs) and update constraints.
     *
     * In addition, saturation will be performed on any variables that require it
     */
    private fun generateCodeForIntraLogic(location: Location, automata: HybridAutomata, indent: Int): String {
        val result = StringBuilder()

        val customVars = HashMap<String, String>(Utils.DEFAULT_CUSTOM_VARIABLES)

        // Go through each flow constraint
        for((variable, equation) in location.flow) {
            // Create the forward-euler equivalent of the ODE
            val eulerSolution = Plus(ParseTreeVariable(variable), Multiply(equation, ParseTreeVariable("STEP_SIZE")))

            // And then generate code that performs the ODE
            result.appendln("${config.getIndent(indent)}${Utils.createVariableName(variable)}_u = ${Utils.generateCodeForParseTreeItem(eulerSolution, Utils.PrefixData("me->", requireSelfReferenceInFunctionCalls, delayedVariableTypes))};")

            // We also keep track of the variables we've added in flow, so that the latest copies can be used in the
            // update section
            customVars[variable] = "${Utils.createVariableName(variable)}_u"
        }

        if(location.flow.isNotEmpty())
            result.appendln()

        // Now go through each update constraint
        for((variable, equation) in location.update) {
            // Generate code that performs the given update
            result.appendln("${config.getIndent(indent)}${Utils.createVariableName(variable)}_u = ${Utils.generateCodeForParseTreeItem(equation, Utils.PrefixData("me->", requireSelfReferenceInFunctionCalls, delayedVariableTypes, customVars))};")
        }

        if(location.update.isNotEmpty())
            result.appendln()

        // Now we look at saturation, so collect all the limits
        val saturationLimits = CodeGenManager.collectSaturationLimits(location, automata.edges)

        // And now for each limit we need to add in the check for it
        for((point, dependencies) in saturationLimits) {
            // Get the variable that we'll be checking for saturation
            val variable = Utils.createVariableName(point.variable)
            // And the limit
            val limit = Utils.generateCodeForParseTreeItem(point.value, Utils.PrefixData("me->", requireSelfReferenceInFunctionCalls, delayedVariableTypes))

            // Now, depending on the direction of saturation we'll have a different equatio that we generate for the check
            val condition = when(point.direction) {
                SaturationDirection.RISING -> "${variable}_u > $limit && me->$variable < $limit" // Variable is now above limit, but wasn't before
                SaturationDirection.FALLING -> "${variable}_u < $limit && me->$variable > $limit" // Variable is now below limit, but wasn't before
                SaturationDirection.BOTH -> "(${variable}_u > $limit && me->$variable < $limit) || (${variable}_u < $limit && me->$variable > $limit)" // Either of the above
            }

            // Add the code that checks for the saturation condition
            result.appendln("${config.getIndent(indent)}if($condition) {")
            result.appendln("${config.getIndent(indent+1)}// Need to saturate ${point.variable} to $limit")

            // Now we have the code that actually performs the saturation
            if(dependencies.isNotEmpty()) {
                // All dependencies that we allow are linear combinations, so we work out at what time the limit would
                // have been hit "frac", and then use that to scale each of the values with respect to their previous
                // values
                result.appendln("${config.getIndent(indent+1)}// Also some dependencies that need saturating")
                result.appendln("${config.getIndent(indent+1)}double frac = ($limit - me->$variable) / (${variable}_u - me->$variable);")
                result.appendln()

                // Now do the scaling with respect to their previous values
                dependencies
                        .map { Utils.createVariableName(it) }
                        .forEach { result.appendln("${config.getIndent(indent+1)}${it}_u = me->$it + frac * (${it}_u - me->$it);") }
                result.appendln()
            }

            // And saturate the actual variable we're looking at
            result.appendln("${config.getIndent(indent+1)}${variable}_u = $limit;")
            // Close the check for saturation
            result.appendln("${config.getIndent(indent)}}")
        }

        if(saturationLimits.isNotEmpty())
            result.appendln()

        // Return the intra-location state machine
        return result.toString()
    }

    /**
     * Generates a string that creates an intermediate instantiate of a given Variable
     */
    private fun generateIntermediateVariable(variable: Variable): String {
        // This will be of the same type, and start with the same value, just have a different name
        return "${Utils.generateCType(variable.type)} ${Utils.createVariableName(variable.name)}_u = me->${Utils.createVariableName(variable.name)};"
    }

    /**
     * Generates a string that updates a given Variable from its intermediate instantiate
     */
    private fun updateFromIntermediateVariable(variable: Variable): String {
        // This sets the master copy of the variable to take the value of the intermediate version
        return "me->${Utils.createVariableName(variable.name)} = ${Utils.createVariableName(variable.name)}_u;"
    }

    /**
     * Generates execution code that's specific to Hybrid Networks
     */
    private fun generateNetworkExecutionFunction(network: HybridNetwork): String {
        val result = StringBuilder()

        // Let's start by adding an entry to each delayed variable that we have to keep track of
        if (network.variables.any { it.canBeDelayed() }) {
            for (variable in network.variables.filter { it.canBeDelayed() })
                result.appendln("${config.getIndent(1)}${Utils.createFunctionName("Delayable", Utils.generateCType(variable.type), "Add")}(&me->${Utils.createVariableName(variable.name, "delayed")}, me->${Utils.createVariableName(variable.name)});")
            result.appendln()
        }

        // Get a list of the inputs that we're assigning to, in a sorted order so it looks slightly nicer
        val keys = network.ioMapping.keys.sortedWith(compareBy({ it.automata }, { it.variable }))

        // And get a map of all the instantiate names that we need to use here (they're formatted differently to variables)
        val customVariableNames = network.instances.mapValues { "me->${ Utils.createVariableName(it.key, "data")}" }
        //customVariableNames.putAll(network.variables.associate {  })

        // Now we go through each input
        var prev: String? = null
        for (key in keys) {
            if (prev != null && prev != key.automata)
                result.appendln()

            // Header for output mappings, when needed
            if(prev != key.automata && key.automata.isBlank())
                result.appendln("${config.getIndent(1)}// Output Mapping")

            // Header for variable mappings, when needed
            if(prev != key.automata && prev != null && prev.isBlank())
                result.appendln("${config.getIndent(1)}// Mappings")

            prev = key.automata

            // And assign to the input, the correct output (or combination of them)
            val from = network.ioMapping[key]!!
            if(key.automata.isBlank())
                // We are writing to an output of this automata
                result.appendln("${config.getIndent(1)}me->${Utils.createVariableName(key.variable)} = ${Utils.generateCodeForParseTreeItem(from, Utils.PrefixData("me->", requireSelfReferenceInFunctionCalls, customVariableNames = customVariableNames))};")
            else
                // We are writing to an input of a sub-automata
                result.appendln("${config.getIndent(1)}me->${Utils.createVariableName(key.automata, "data")}.${Utils.createVariableName(key.variable)} = ${Utils.generateCodeForParseTreeItem(from, Utils.PrefixData("me->", requireSelfReferenceInFunctionCalls, customVariableNames = customVariableNames))};")
        }

        result.appendln()
        result.appendln()

        // Let's start the run code
        result.appendln("${config.getIndent(1)}// Run Automata")

        // We go through each instantiate we've created
        var first = true
        for ((name, instance) in objects) {
            if (!first)
                result.appendln()
            first = false

            // And simply call the "Run" function for it
            result.appendln("${config.getIndent(1)}${Utils.createFunctionName(instance, "Run")}(&me->${Utils.createVariableName(name, "data")});")
        }

        // And return the collection of "Run" functions
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