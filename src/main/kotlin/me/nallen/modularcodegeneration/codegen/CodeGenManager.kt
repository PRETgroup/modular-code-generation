package me.nallen.modularcodegeneration.codegen

import com.fasterxml.jackson.module.kotlin.*
import me.nallen.modularcodegeneration.codegen.c.CCodeGenerator
import me.nallen.modularcodegeneration.codegen.vhdl.VHDLGenerator
import me.nallen.modularcodegeneration.hybridautomata.*
import me.nallen.modularcodegeneration.hybridautomata.Locality
import me.nallen.modularcodegeneration.parsetree.*
import me.nallen.modularcodegeneration.parsetree.Variable
import java.io.File
import java.util.*

typealias ParseTreeLocality = me.nallen.modularcodegeneration.parsetree.Locality

/**
 * The class that facilitates code generation from a Hybrid Network.
 *
 * This class also provides several utility methods for things such as saturation, and parametrisation of the instances
 */
object CodeGenManager {
    private val mapper = jacksonObjectMapper()

    /**
     * Generate code for a Hybrid Network in the given language. The code will be placed into the provided directory,
     * overwriting any contents that may already exist.
     *
     * A set of configuration properties can also be set to modify the structure of the generated code
     */
    fun generate(item: HybridItem, language: CodeGenLanguage, dir: String, config: Configuration = Configuration()) {
        val outputDir = File(dir)

        // If the desired output directory already exists and is a file, then we stop!
        if(outputDir.exists() && !outputDir.isDirectory)
            throw IllegalArgumentException("Desired output directory $dir is not a directory!")

        // Easiest way to clear the directory is to recursively delete, then recreate
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        if(item is HybridNetwork)
            createInstantiates(item, config)

        // Depending on the language, we want to call a different generator.
        when(language) {
            CodeGenLanguage.C -> CCodeGenerator.generate(item, dir, config)
            CodeGenLanguage.VHDL -> VHDLGenerator.generate(item, dir, config)
        }
    }

    private fun createInstantiates(network: HybridNetwork, config: Configuration) {
        if(config.parametrisationMethod == ParametrisationMethod.COMPILE_TIME) {
            for((name, instance) in network.instances) {
                val instantiate = network.getInstantiateForInstantiateId(instance.instantiate)
                if(instantiate != null) {
                    val instantiateId = UUID.randomUUID()
                    network.instantiates[instantiateId] = AutomataInstantiate(instantiate.definition, name)
                    instance.instantiate = instantiateId
                }
            }
        }
        else {
            for((_, instance) in network.instances) {
                val instantiate = network.getInstantiateForInstantiateId(instance.instantiate)
                val definition = network.getDefinitionForInstantiateId(instance.instantiate)

                if(instantiate != null && definition != null) {
                    instantiate.name = definition.name

                    val instantiateIds = network.instantiates.filter{it.value.definition == instantiate.definition && it.key != instance.instantiate }.keys

                    for((_, instance2) in network.instances.filter{instantiateIds.contains(it.value.instantiate)})
                        instance2.instantiate = instance.instantiate

                    for(id in instantiateIds)
                        network.instantiates.remove(id)
                }
            }
        }

        for((_, definition) in network.definitions) {
            if(definition is HybridNetwork)
                createInstantiates(definition, config)
        }
    }

    /**
     * Creates a parametrised Hybrid Automata instantiate for the given AutomataInstance pair in a network.
     * All parameters will get their values set to the values provided in the AutomataInstance, or their default value
     * if not present in the AutomataInstance map.
     */
    fun createParametrisedItem(network: HybridNetwork, name: String, instance: AutomataInstance): HybridItem? {
        // We need to make sure that the instantiate actually exists
        val definition = network.getDefinitionForInstantiateId(instance.instantiate)
        if(definition != null) {
            val type = definition.javaClass

            // This is currently a really hacky way to do a deep copy, JSON serialize it and then deserialize.
            // Bad for performance, but easy to do. Hopefully can be fixed later?
            val json = mapper.writeValueAsString(definition)

            val item = mapper.readValue(json, type)

            if(item is HybridNetwork)
                item.parent = network

            // The name becomes the new name
            item.name = name

            val functionTypes = LinkedHashMap<String, VariableType?>()

            if(item is HybridAutomata) {
                // We need to parametrise every function
                for(function in item.functions) {
                    // All variables that are either inputs, or parameters, end up the same - they get set externally.
                    val inputs = ArrayList(function.inputs)
                    inputs.addAll(item.variables.filter {it.locality == Locality.PARAMETER}.map { VariableDeclaration(it.name, it.type, ParseTreeLocality.EXTERNAL_INPUT, it.defaultValue) })

                    // So now we collect all internal variables given we know about the external inputs and parameters
                    function.logic.collectVariables(inputs, functionTypes)

                    // Get the return type, and keep track of it too
                    function.returnType = function.logic.getReturnType(functionTypes)
                    functionTypes[function.name] = function.returnType
                }
            }

            // Now for the new automata, we want to set the value for each parameter
            for ((key, value) in instance.parameters) {
                item.setParameterValue(key, value)
            }

            // And then set parameters to their default value for any that weren't set
            item.setDefaultParametrisation()

            // And return the new parametrised automata
            return item
        }

        // If we can't find a matching automata, then we just return null
        return null
    }

    /**
     * Collects and returns the list of fields that should be logged in the Hybrid Network.
     * This is based off of the provided Configuration properties where the set of logging fields can be provided or, if
     * the field is omitted, all outputs are logged by default
     */
    fun collectFieldsToLog(item: HybridItem, config: Configuration): List<LoggingField> {
        // The list that we'll use to store logging fields
        val toLog = ArrayList<LoggingField>()

        // Check if the user provided any logging fields
        if(config.logging.fields == null) {
            // Fetch all outputs inside the definition
            val outputs = item.variables.filter {it.locality == Locality.EXTERNAL_OUTPUT}
            // And add to the logging fields list
            outputs.mapTo(toLog) { LoggingField(it.name, it.type) }
        }
        else {
            // The user specified some logging fields they want, so let's go through each one
            for(field in config.logging.fields) {
                // Check that a variable of the same name exists in the definition
                if(item.variables.any {it.locality == Locality.EXTERNAL_OUTPUT && it.name == field}) {
                    val output = item.variables.first {it.locality == Locality.EXTERNAL_OUTPUT && it.name == field}

                    // Yay we found everything we needed to, now we can add it to the logging fields list
                    toLog.add(LoggingField(output.name, output.type))
                }
            }
        }

        // Finally we can return the list of fields to log
        return toLog
    }

    /**
     * Collects and returns any saturation limits that exist for a given location.
     *
     * Saturation limits are points where a transition may be missed because of overshooting. In this implementation,
     * saturation occurs whenever we are *exiting* an interval (an equal to is the same as an interval of 0 width).
     *
     *   e.g. guard has x >= 100 -- saturate x at 100 when falling
     *   e.g. guard has x == 100 -- saturate x at 100 when both rising and falling
     *   e.g. guard has x >= 100 && x <= 120 -- saturate x at 100 when falling and 120 when rising
     */
    fun collectSaturationLimits(location: Location, edges: List<Edge>): Map<SaturationPoint, List<String>> {
        val limits = HashMap<SaturationPoint, List<String>>()

        // We need to find every exiting transition from the location
        for(edge in edges.filter {it.fromLocation == location.name}) {
            // Now for the current transition, we want to find every comparison (greater than, less than, etc.) that
            // is performed in the transition's guard
            val comparisons = collectComparisonsFromParseTree(edge.guard)

            // Now we can go through the comparisons and collect saturation points
            val saturationPoints = ArrayList<SaturationPoint>()
            for(comparison in comparisons) {
                var variable: String
                var value: ParseTreeItem

                // We only care about comparisons where one of the operands of the comparison is a Variable
                // So we want to collect the "variable" and the "value"
                if(comparison.getChildren()[0] is Variable) {
                    variable = comparison.getChildren()[0].generateString()
                    value = comparison.getChildren()[1]
                }
                else if(comparison.getChildren()[1] is Variable) {
                    variable = comparison.getChildren()[1].generateString()
                    value = comparison.getChildren()[0]
                }
                else {
                    // Otherwise if neither of the operands are a variable then we can't saturate
                    continue
                }

                // Depending on the type of comparison we get a different saturation direction
                // This depends on if the point at which we "transition" (equal to the value) is the last point in the
                // rising or falling direction
                val saturationDirection = if (comparison is LessThan || comparison is GreaterThanOrEqual) {
                    // >= and < are the opposites of each other, and affect the falling direction
                    SaturationDirection.FALLING
                }
                else if(comparison is GreaterThan || comparison is LessThanOrEqual) {
                    // <= and > are the opposites of each other, and affect the rising direction
                    SaturationDirection.RISING
                }
                else {
                    // == and != are the opposites of each other, and affect both directions
                    SaturationDirection.BOTH
                }

                // Add the saturation point to the list
                saturationPoints.add(SaturationPoint(variable, value, saturationDirection))
            }

            // Next, we need to only make a note of the variables which we can actually saturate (i.e. internal
            // variables whose value is updated by either flow or update).
            // As one further step, we also need to go through and find whether the saturated variable is dependent on
            // any other variables for its value. If this is the case then we will also need to make a note of those
            // variables too so that they can also be saturated.
            for(saturationPoint in saturationPoints) {
                val variableName = saturationPoint.variable
                // If the saturated variable is updated inside the location (either flow or update) then we can saturate
                if(location.flow.containsKey(variableName) || location.update.containsKey(variableName))
                    // So let's add it and its dependencies to a map
                    limits[saturationPoint] = getDependenciesForSaturation(variableName, location)
            }
        }

        return limits
    }

    /**
     * Gets a list of variables that are dependencies of another variable
     */
    private fun getDependenciesForSaturation(variable: String, location: Location, updateStack: ArrayList<String> = ArrayList()): List<String> {
        val dependencies = ArrayList<String>()

        // If we've come full circle and are trying to find dependencies of something we already have, then we stop
        if(updateStack.contains(variable))
            return dependencies

        // The stack is used as an escape condition from the recursion
        updateStack.add(variable)

        // If this variable is defined in the flow, then it's an ODE, so it doesn't have any direct dependencies
        // Instead, only its gradient depends on other variables, so we can ignore that
        if(location.flow.containsKey(variable)) {
            return dependencies
        }

        // The other case is that this variable is part of an update
        if(location.update.containsKey(variable)) {
            // Check if the operations in the update are amenable to saturation
            // Currently, can only use PLUS, MINUS, NEGATIVE operators to keep it linear

            // Get all operations
            val operations = collectOperationsFromParseTree(location.update[variable]!!)

            // Check if any don't satisfy the constraints
            if(operations.any {it != "plus" && it != "minus" && it != "negative"}) {
                // Can't saturate
                throw IllegalArgumentException("Unable to saturate update formula ${location.update[variable]!!.generateString()}")
            }

            // Otherwise we can saturate this update!
            // So let's fetch all the variables involved in this update, and add them as dependencies
            val subDependencies = collectVariablesFromParseTree(location.update[variable]!!)
            dependencies.addAll(subDependencies.filter {!dependencies.contains(it)})

            // And then we need to go through and get all the sub-dependencies for those too
            for(subDependency in subDependencies) {
                dependencies.addAll(getDependenciesForSaturation(subDependency, location, updateStack).filter {!dependencies.contains(it)})
            }
        }

        // Return the list of dependencies
        return dependencies
    }

    /**
     * Collects and returns a list of variables that are present within the given ParseTreeItem
     */
    private fun collectVariablesFromParseTree(item: ParseTreeItem): List<String> {
        val variables = ArrayList<String>()

        // If the current item is a Variable
        if(item is Variable) {
            // So long as we don't already know about this
            if(!variables.contains(item.name))
                // Then we can add it to the list!
                variables.add(item.name)
        }

        // For any children that exist from this item
        for(child in item.getChildren())
            // We recursively parse the tree to find any more variables
            variables.addAll(collectVariablesFromParseTree(child))

        // Return the list of variables
        return variables
    }

    /**
     * Collects and returns a list of operations that are present within the given ParseTreeItem
     */
    private fun collectOperationsFromParseTree(item: ParseTreeItem): List<String> {
        val operands = ArrayList<String>()

        // If the current item is not a literal or variable, it must be an operation
        if(item !is Literal && item !is Variable) {
            // So long as we don't already know about this
            if(!operands.contains(item.type))
                // Then we can add it to the list!
                operands.add(item.type)
        }

        // For any children that exist from this item
        for(child in item.getChildren()) {
            // We recursively parse the tree to find any more operations
            operands.addAll(collectOperationsFromParseTree(child))
        }

        // Return the list of operators
        return operands
    }

    /**
     * Collects and returns a list of Comparator ParseTreeItems that are present within the given ParseTreeItem
     */
    private fun collectComparisonsFromParseTree(item: ParseTreeItem): List<ParseTreeItem> {
        val comparisons = ArrayList<ParseTreeItem>()

        // If we've found a comparison
        if(item is Equal || item is GreaterThan || item is GreaterThanOrEqual || item is LessThan || item is LessThanOrEqual) {
            // Then we add it to the list!
            // Note that here we add the complete item, so that we can perform logic on the operands later
            comparisons.add(item)
        }
        else {
            // Otherwise it wasn't a comparison, so for any children that exist from this item
            for(child in item.getChildren()) {
                // We recursively collect the comparisons that exist from this item
                comparisons.addAll(collectComparisonsFromParseTree(child))
            }
        }

        // Return the list of comparisons
        return comparisons
    }
}

/**
 * A class that captures the properties of a Saturation Point, a point at which a variable's value should get saturated
 */
data class SaturationPoint(
        // The name of the variable to saturate
        val variable: String,

        // The value at which it should be saturated
        val value: ParseTreeItem,

        // The direction in which it should be saturated
        val direction: SaturationDirection
)

/**
 * An enum that represents the direction in which something should be saturated
 */
enum class SaturationDirection {
    RISING, FALLING, BOTH
}

/**
 * A class that captures a field to be logged
 */
data class LoggingField(
        // The variable to be logged
        val variable: String,

        // The type of the variable to be logged
        val type: VariableType
)

/**
 * An enum that represents the language for which code generation should be performed
 */
enum class CodeGenLanguage {
    C,
    VHDL
}