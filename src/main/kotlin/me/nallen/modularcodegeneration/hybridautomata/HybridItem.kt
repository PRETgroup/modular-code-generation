package me.nallen.modularcodegeneration.hybridautomata

import me.nallen.modularcodegeneration.parsetree.ParseTreeItem
import me.nallen.modularcodegeneration.parsetree.VariableType
import me.nallen.modularcodegeneration.parsetree.getChildren
import me.nallen.modularcodegeneration.parsetree.setParameterValue
import me.nallen.modularcodegeneration.parsetree.Variable as ParseTreeVariable

abstract class HybridItem(
        open var name: String = "Item",

        val variables: ArrayList<Variable> = ArrayList()
) {
    fun addContinuousVariable(item: String, locality: Locality = Locality.INTERNAL, default: ParseTreeItem? = null, delayableBy: ParseTreeItem? = null): HybridItem {
        if(!variables.any {it.name == item}) {
            variables.add(Variable(item, VariableType.REAL, locality, default, delayableBy))

            if(default != null)
                checkParseTreeForNewContinuousVariable(default)
        }

        return this
    }

    fun addEvent(item: String, locality: Locality = Locality.INTERNAL, delayableBy: ParseTreeItem? = null): HybridItem {
        if(!variables.any {it.name == item}) {
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

    open fun validate(): Boolean {
        return true
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