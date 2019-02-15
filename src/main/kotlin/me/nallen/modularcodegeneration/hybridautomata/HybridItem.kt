package me.nallen.modularcodegeneration.hybridautomata

import me.nallen.modularcodegeneration.logging.Logger
import me.nallen.modularcodegeneration.parsetree.*
import me.nallen.modularcodegeneration.parsetree.Variable as ParseTreeVariable
import me.nallen.modularcodegeneration.parsetree.Locality as ParseTreeLocality

abstract class HybridItem(
        open var name: String = "Item",

        val variables: ArrayList<Variable> = ArrayList()
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
    open fun validate(): Boolean {
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
}