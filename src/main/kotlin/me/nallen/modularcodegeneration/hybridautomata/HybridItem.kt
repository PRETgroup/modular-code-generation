package me.nallen.modularcodegeneration.hybridautomata

import me.nallen.modularcodegeneration.logging.Logger
import me.nallen.modularcodegeneration.parsetree.ParseTreeItem
import me.nallen.modularcodegeneration.parsetree.VariableType
import me.nallen.modularcodegeneration.parsetree.getChildren
import me.nallen.modularcodegeneration.parsetree.setParameterValue
import me.nallen.modularcodegeneration.parsetree.Variable as ParseTreeVariable

abstract class HybridItem(
        open var name: String = "Item",

        val variables: ArrayList<Variable> = ArrayList()
) {
    fun addContinuousVariable(item: String, locality: Locality = Locality.INTERNAL, default: ParseTreeItem? = null, delayableBy: ParseTreeItem? = null, forceAdd: Boolean = false): HybridItem {
        if(forceAdd || !variables.any {it.name == item}) {
            variables.add(Variable(item, VariableType.REAL, locality, default, delayableBy))

            if(default != null)
                checkParseTreeForNewContinuousVariable(default)
        }

        return this
    }

    fun addEvent(item: String, locality: Locality = Locality.INTERNAL, delayableBy: ParseTreeItem? = null, forceAdd: Boolean = false): HybridItem {
        if(forceAdd || !variables.any {it.name == item}) {
            variables.add(Variable(item, VariableType.BOOLEAN, locality, delayableBy = delayableBy))
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
            Logger.error("Multiple definitions (${list.size} of variable '$name' in '${this.name}'.")
            valid = false
        }

        return valid
    }

    /* Private Methods */

    protected fun checkParseTreeForNewContinuousVariable(item: ParseTreeItem, locality: Locality = Locality.INTERNAL) {
        if(item is ParseTreeVariable) {
            addContinuousVariable(item.name, locality)
        }

        for(child in item.getChildren()) {
            checkParseTreeForNewContinuousVariable(child)
        }
    }

    protected fun checkParseTreeForNewEvent(item: ParseTreeItem, locality: Locality = Locality.INTERNAL) {
        if(item is ParseTreeVariable) {
            addEvent(item.name, locality)
        }

        for(child in item.getChildren()) {
            checkParseTreeForNewEvent(child, locality)
        }
    }
}