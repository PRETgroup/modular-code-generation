package me.nallen.modularcodegeneration.hybridautomata

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import me.nallen.modularcodegeneration.codegen.CodeGenManager
import me.nallen.modularcodegeneration.logging.Logger
import me.nallen.modularcodegeneration.parsetree.*
import me.nallen.modularcodegeneration.parsetree.Variable as ParseTreeVariable
import me.nallen.modularcodegeneration.parsetree.Locality as ParseTreeLocality

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = HybridAutomata::class, name = "automata"),
    JsonSubTypes.Type(value = HybridNetwork::class, name = "network")
)
abstract class HybridItem(
        open var name: String = "Item",

        val variables: ArrayList<Variable> = ArrayList(),

        val functions: ArrayList<FunctionDefinition> = ArrayList()
) {
    fun addVariable(item: String, type: VariableType = VariableType.ANY, locality: Locality = Locality.INTERNAL, default: ParseTreeItem? = null, delayableBy: ParseTreeItem? = null, forceAdd: Boolean = false): HybridItem {
        if(forceAdd || !variables.any {it.name == item}) {
            variables.add(Variable(item, type, locality, default, delayableBy))

            if(default != null)
                checkParseTreeForNewVariables(default, type)
        }
        else {
            variables.filter { it.name == item && it.type == VariableType.ANY }.forEach { it.type = type }
        }

        return this
    }

    open fun setParameterValue(key: String, value: ParseTreeItem) {
        // Check parameter exists
        if(!variables.any {it.locality == Locality.PARAMETER && it.name == key})
            return

        // Remove parameter from list
        variables.removeIf {it.locality == Locality.PARAMETER && it.name == key}

        // Parametrise delayables
        for(variable in variables.filter {it.canBeDelayed()}) {
            variable.delayableBy!!.setParameterValue(key, value)
        }

        // Parametrise functions
        for(function in functions) {
            for(input in function.inputs) {
                input.defaultValue?.setParameterValue(key, value)
            }

            function.logic.setParameterValue(key, value)
        }
    }

    fun setDefaultParametrisation() {
        for(variable in variables.filter {it.locality == Locality.PARAMETER && it.defaultValue != null}) {
            this.setParameterValue(variable.name, variable.defaultValue!!)
        }
    }

    open fun flatten(): HybridItem {
        return this
    }

    /**
     * Check if the basic parts of this Hybrid Item are valid, this only involves variables. This can help the user
     * detect errors during the compile stage rather than by analysing the generated code.
     */
    open fun validate(includeConstants: Boolean = true): Boolean {
        // Let's try see if anything isn't valid
        var valid = true

        // The only thing we really have an issue with is duplicate variable names, so let's check for that
        for((name, list) in variables.groupBy { it.name }.filter { it.value.size > 1 }) {
            Logger.error("Multiple definitions (${list.size}) of variable '$name' in '${this.name}'.")
            valid = false
        }

        // We'll also check for variables that are of unknown type
        for(variable in variables.filter { it.type == VariableType.ANY }) {
            Logger.error("Unable to detect type for variable '${variable.name}' in '$name'.")
            valid = false
        }

        // Let's first start by checking through all functions
        val functionArgumentsMap = HashMap<String, List<VariableType>>()
        val functionReturnMap = HashMap<String, VariableType?>()
        for(function in functions) {
            // When checking the body we want to keep track of what variables can be written to or read from
            val writeableVars = function.logic.variables
                    .filter { it.locality == ParseTreeLocality.INTERNAL }
                    .map { Pair(it.name, it.type) }.toMap()
            val readableVars = HashMap<String, VariableType>()
            readableVars.putAll(function.logic.variables
                    .filter { it.locality == ParseTreeLocality.INTERNAL || it.locality == ParseTreeLocality.EXTERNAL_INPUT }
                    .plus(function.inputs)
                    .map { Pair(it.name, it.type) }.toMap())

            if(includeConstants)
                readableVars.putAll(CodeGenManager.CODEGEN_CONSTANTS)

            for(variable in function.logic.variables.filter { it.type == VariableType.ANY }) {
                Logger.error("Unable to detect type for variable '${variable.name}' in function '${function.name}' of '$name'.")
                valid = false
            }

            // And then validate the function body
            valid = valid and validateFunction(function.logic, readableVars, writeableVars, functionReturnMap, functionArgumentsMap, "function '${function.name}' of '$name'")

            if(function.returnType == VariableType.ANY) {
                Logger.error("Invalid return type of '${function.returnType}' for function '${function.name}' of '$name'.")
                valid = false
            }

            // We need to store function types so we can make sure it's called correctly
            functionArgumentsMap[function.name] = function.inputs.map { it.type }

            // And keep track of what it returns
            functionReturnMap[function.name] = function.returnType
        }

        return valid
    }

    /* Private Methods */

    protected fun checkParseTreeForNewVariables(item: ParseTreeItem, currentType: VariableType, functionArguments: Map<String, List<VariableType>> = mapOf(), locality: Locality = Locality.INTERNAL) {
        if(item is ParseTreeVariable) {
            addVariable(item.name, currentType, locality)
        }

        val expectedTypes = item.getExpectedTypes(functionArguments)

        val children = item.getChildren()

        if(item is Equal || item is NotEqual) {
            val childType0 = children[0].getOperationResultType()
            val childType1 = children[1].getOperationResultType()

            if(childType0 != VariableType.ANY && childType1 == VariableType.ANY) {
                checkParseTreeForNewVariables(children[0], childType0, functionArguments)
                checkParseTreeForNewVariables(children[1], childType0, functionArguments)
            }
            else if(childType1 != VariableType.ANY && childType0 == VariableType.ANY) {
                checkParseTreeForNewVariables(children[0], childType1, functionArguments)
                checkParseTreeForNewVariables(children[1], childType1, functionArguments)
            }
            else {
                checkParseTreeForNewVariables(children[0], childType0, functionArguments)
                checkParseTreeForNewVariables(children[1], childType1, functionArguments)
            }
        }
        else {
            for((index, child) in children.withIndex()) {
                if(index < expectedTypes.size)
                    checkParseTreeForNewVariables(child, expectedTypes[index], functionArguments)
                else
                    checkParseTreeForNewVariables(child, VariableType.ANY, functionArguments)
            }
        }
    }

    protected fun validateWritingVariables(eq: ParseTreeItem, readableVars: List<String>, writeableVars: List<String>, location: String = "'$name'"): Boolean {
        // Let's try see if anything isn't valid
        var valid = true

        // Let's go through every variable
        for(variable in eq.collectVariables()) {
            // Check if we know about this variable and can write to it
            if(!writeableVars.contains(variable)) {
                // If we try to write to a read-only variable we can have a different error message
                if(readableVars.contains(variable))
                    Logger.error("Unable to write to read-only variable '$variable' in $location.")
                else
                    Logger.error("Unable to write to unknown variable '$variable' in $location'.")

                // Regardless, it's an issue
                valid = false
            }
        }

        return valid
    }

    protected fun validateReadingVariables(eq: ParseTreeItem, readableVars: List<String>, writeableVars: List<String>, location: String = "'$name'"): Boolean {
        // Let's try see if anything isn't valid
        var valid = true

        // Let's go through every variable
        for(variable in eq.collectVariables()) {
            // Check if we know about this variable and can write to it
            if(!readableVars.contains(variable)) {
                // If we try to read from a write-only variable we can have a different error message
                if(writeableVars.contains(variable))
                    Logger.error("Unable to read from write-only variable '$variable' in $location.")
                else
                    Logger.error("Unable to read from unknown variable '$variable' in $location.")

                // Regardless, it's an issue
                valid = false
            }
        }

        return valid
    }

    /**
     * Check if a function program is valid in terms of its definition. This will check that every variable used is used
     * correctly (e.g. inputs aren't written to, etc.).
     */
    protected fun validateFunction(program: Program, readableVars: Map<String, VariableType>, writeableVars: Map<String, VariableType>, functionTypes: Map<String, VariableType?>, functionArguments: Map<String, List<VariableType>>, location: String = "'$name'", lineNumberStart: Int = 1, inLoop: Boolean = false): Boolean {
        var valid = true

        // We keep track of the line number to be somewhat helpful when printing out errors
        var lineNumber = lineNumberStart

        // We need to iterate over every line
        for(line in program.lines) {
            // And then depending on what the line is, check each component of it
            when(line) {
                is Statement -> {
                    valid = valid and validateReadingVariables(line.logic, readableVars.keys.toList(), writeableVars.keys.toList(), "line $lineNumber of $location")
                    valid = valid and line.logic.validate(readableVars.plus(writeableVars), functionTypes, functionArguments, "line $lineNumber of $location")

                    lineNumber++
                }
                is Break -> {
                    if(!inLoop) {
                        Logger.error("Break statement found at unexpected location on line $lineNumber of $location.")
                        valid = false
                    }
                }
                is Assignment -> {
                    valid = valid and validateWritingVariables(ParseTreeVariable(line.variableName.name), readableVars.keys.toList(), writeableVars.keys.toList(), "line $lineNumber of $location")
                    valid = valid and validateReadingVariables(line.variableValue, readableVars.keys.toList(), writeableVars.keys.toList(), "line $lineNumber of $location")
                    valid = valid and line.variableValue.validate(readableVars.plus(writeableVars), functionTypes, functionArguments, "line $lineNumber of $location")

                    // For assignments we should also check that we're assigning correct values
                    if(writeableVars.containsKey(line.variableName.name)) {
                        val assignType = line.variableValue.getOperationResultType(readableVars.plus(writeableVars))
                        if(writeableVars[line.variableName.name] != assignType && writeableVars[line.variableName.name] != VariableType.ANY) {
                            Logger.error("Incorrect type assigned to variable '${line.variableName.name}' in line $lineNumber of $location." +
                                    " Found '$assignType', expected '${writeableVars[line.variableName.name]}'")
                            valid = false
                        }
                    }

                    lineNumber++
                }
                is Return -> {
                    valid = valid and validateReadingVariables(line.logic, readableVars.keys.toList(), writeableVars.keys.toList(), "line $lineNumber of $location")
                    valid = valid and line.logic.validate(readableVars.plus(writeableVars), functionTypes, functionArguments, "line $lineNumber of $location")

                    lineNumber++
                }
                is IfStatement -> {
                    // Statements with their own bodies also need to be recursively checked
                    valid = valid and validateFunction(line.body, readableVars, writeableVars, functionTypes, functionArguments, location, lineNumber+1, inLoop)
                    valid = valid and validateReadingVariables(line.condition, readableVars.keys.toList(), writeableVars.keys.toList(), "line $lineNumber of $location")
                    valid = valid and line.condition.validate(readableVars.plus(writeableVars), functionTypes, functionArguments, "line $lineNumber of $location")

                    lineNumber += line.body.getTotalLines() + 2
                }
                is ElseStatement -> {
                    // Statements with their own bodies also need to be recursively checked
                    valid = valid and validateFunction(line.body, readableVars, writeableVars, functionTypes, functionArguments, location, lineNumber+1, inLoop)

                    lineNumber += line.body.getTotalLines() + 2
                }
                is ElseIfStatement -> {
                    // Statements with their own bodies also need to be recursively checked
                    valid = valid and validateFunction(line.body, readableVars, writeableVars, functionTypes, functionArguments, location, lineNumber+1, inLoop)
                    valid = valid and validateReadingVariables(line.condition, readableVars.keys.toList(), writeableVars.keys.toList(), "line $lineNumber of $location")
                    valid = valid and line.condition.validate(readableVars.plus(writeableVars), functionTypes, functionArguments, "line $lineNumber of $location")

                    lineNumber += line.body.getTotalLines() + 2
                }
                is ForStatement -> {
                    // Check that the loop variable isn't already used
                    if(readableVars.plus(writeableVars).containsKey(line.variableName.name)) {
                        Logger.error("Loop variable '${line.variableName.name}' already assigned in line $lineNumber of $location.")
                        valid = false
                    }

                    // Add the loop variable as a readable variable
                    val innerVars = HashMap(readableVars)
                    innerVars[line.variableName.name] = VariableType.INTEGER

                    // For loops with their own bodies also need to be recursively checked
                    valid = valid and validateFunction(line.body, innerVars, writeableVars, functionTypes, functionArguments, location, lineNumber+1, true)

                    lineNumber += line.body.getTotalLines() + 2
                }
            }
        }

        return valid
    }
}