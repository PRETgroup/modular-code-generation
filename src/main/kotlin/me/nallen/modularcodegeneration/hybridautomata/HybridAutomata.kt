package me.nallen.modularcodegeneration.hybridautomata

import me.nallen.modularcodegeneration.description.haml.ParseTreeLocality
import me.nallen.modularcodegeneration.logging.Logger
import me.nallen.modularcodegeneration.parsetree.*
import me.nallen.modularcodegeneration.parsetree.Variable as ParseTreeVariable

/**
 * Created by nall426 on 31/05/2017.
 */

data class HybridAutomata(
        override var name: String = "HA",

        val locations: ArrayList<Location> = ArrayList(),
        val edges: ArrayList<Edge> = ArrayList(),
        var init: Initialisation = Initialisation("")
) : HybridItem() {
    fun addLocation(location: Location): HybridAutomata {
        val variableTypes = variables.map { Pair(it.name, it.type) }.toMap()
        val functionTypes = functions.map { Pair(it.name, it.returnType) }.toMap()
        val functionArguments = functions.map { Pair(it.name, it.inputs.map { it.type }) }.toMap()

        // Check for any continuous variables
        checkParseTreeForNewVariables(location.invariant, VariableType.BOOLEAN, functionArguments)

        for((key, value) in location.flow) {
            // Flow constraints have to be REAL
            addVariable(key, VariableType.REAL)
            checkParseTreeForNewVariables(value, VariableType.REAL, functionArguments)
        }

        for((key, value) in location.update) {
            // Updates could be anything, so let's guess the type
            val type = value.getOperationResultType(variableTypes, functionTypes)
            addVariable(key, type)
            checkParseTreeForNewVariables(value, type, functionArguments)
        }

        locations.add(location)

        return this
    }

    fun addEdge(edge: Edge): HybridAutomata {
        val variableTypes = variables.map { Pair(it.name, it.type) }.toMap()
        val functionTypes = functions.map { Pair(it.name, it.returnType) }.toMap()
        val functionArguments = functions.map { Pair(it.name, it.inputs.map { it.type }) }.toMap()

        // Check for any continuous variables
        checkParseTreeForNewVariables(edge.guard, VariableType.BOOLEAN, functionArguments)

        for((key, value) in edge.update) {
            // Updates could be anything, so let's guess the type
            val type = value.getOperationResultType(variableTypes, functionTypes)
            addVariable(key, type)
            checkParseTreeForNewVariables(value, type, functionArguments)
        }

        edges.add(edge)

        return this
    }

    override fun setParameterValue(key: String, value: ParseTreeItem) {
        super.setParameterValue(key, value)

        // Parametrise all transitions
        for(edge in edges) {
            // Guards
            edge.guard.setParameterValue(key, value)

            // Updates
            for((_, update) in edge.update) {
                update.setParameterValue(key, value)
            }
        }

        // Parametrise all locations
        for(location in locations) {
            // Invariant
            location.invariant.setParameterValue(key, value)

            // Flows
            for((_, flow) in location.flow) {
                flow.setParameterValue(key, value)
            }

            // Updates
            for((_, update) in location.update) {
                update.setParameterValue(key, value)
            }
        }

        // Parametrise initialisation
        for((_, valuation) in init.valuations) {
            valuation.setParameterValue(key, value)
        }
    }

    /**
     * Check if this Hybrid Automata is valid, this includes things like variable names being correct, transitions being
     * between valid locations, etc. This can help the user detect errors during the compile stage rather than by
     * analysing the generated code.
     */
    override fun validate(): Boolean {
        // Let's try see if anything isn't valid
        var valid = super.validate()

        val writeableVars = ArrayList<String>()
        val readableVars = ArrayList<String>()
        val variableTypes = HashMap<String, VariableType>()
        val functionReturnMap = functions.map { Pair(it.name, it.returnType) }.toMap()
        val functionArgumentsMap = functions.map { Pair(it.name, it.inputs.map { it.type }) }.toMap()

        // We need to keep track of what variables we can write to and read from in this network
        for(variable in this.variables) {
            // Keep track of variable types
            variableTypes[variable.name] = variable.type

            // Many things can be read from
            readableVars.add(variable.name)

            // But fewer can be written to
            if(variable.locality == Locality.EXTERNAL_OUTPUT || variable.locality == Locality.INTERNAL) {
                writeableVars.add(variable.name)
            }
        }

        // Check through all the locations
        for(location in locations) {
            // First, let's check the invariant
            valid = valid and validateReadingVariables(location.invariant, readableVars, writeableVars, "invariant of '${location.name}' in '$name'")
            valid = valid and location.invariant.validate(variableTypes, functionReturnMap, functionArgumentsMap, "invariant of '${location.name}' in '$name'")

            // Now we check each flow constraint
            for((to, from) in location.flow) {
                // Check where we're writing to
                valid = valid and validateWritingVariables(ParseTreeVariable(to), readableVars, writeableVars, "flow constraint of '${location.name}' in '$name'")

                // And where the value is coming from
                valid = valid and validateReadingVariables(from, readableVars, writeableVars, "flow constraint for '$to' of '${location.name}' in '$name'")
                valid = valid and from.validate(variableTypes, functionReturnMap, functionArgumentsMap, "flow constraint for '$to' of '${location.name}' in '$name'")

                // Flow constraints should always return REAL numbers
                val assignType = from.getOperationResultType(variableTypes, functionReturnMap)
                if(VariableType.REAL != assignType) {
                    Logger.error("Incorrect type assigned to flow constraint for '$to' of '${location.name}' in '$name'." +
                            " Found '$assignType', expected '${VariableType.REAL}'")
                    valid = false
                }
            }

            // And finally the updates
            for((to, from) in location.update) {
                // Check where we're writing to
                valid = valid and validateWritingVariables(ParseTreeVariable(to), readableVars, writeableVars, "update equation of '${location.name}' in '$name'")

                // And where the value is coming from
                valid = valid and validateReadingVariables(from, readableVars, writeableVars, "update equation for '$to' of '${location.name}' in '$name'")
                valid = valid and from.validate(variableTypes, functionReturnMap, functionArgumentsMap, "update equation for '$to' of '${location.name}' in '$name'")

                // Updates should return correct types
                if(variableTypes.containsKey(to)) {
                    val assignType = from.getOperationResultType(variableTypes, functionReturnMap)
                    if(variableTypes[to] != assignType && variableTypes[to] != VariableType.ANY) {
                        Logger.error("Incorrect type assigned to update equation for '$to' of '${location.name}' in '$name'." +
                                " Found '$assignType', expected '${variableTypes[to]}'")
                        valid = false
                    }
                }
            }
        }

        // Check through each edge between locations
        for(edge in edges) {
            // First, let's check where this edge came from
            if(!locations.any { it.name == edge.fromLocation }) {
                Logger.error("Unknown location '${edge.fromLocation}' used in transition from '${edge.fromLocation}' to '${edge.toLocation}' in '$name'.")
                valid = false
            }

            // And where it goes to
            if(!locations.any { it.name == edge.toLocation }) {
                Logger.error("Unknown location '${edge.toLocation}' used in transition from '${edge.fromLocation}' to '${edge.toLocation}' in '$name'.")
                valid = false
            }

            // Next, let's check the guard
            valid = valid and validateReadingVariables(edge.guard, readableVars, writeableVars, "guard of transition '${edge.fromLocation} -> ${edge.toLocation}' in '$name'")
            valid = valid and edge.guard.validate(variableTypes, functionReturnMap, functionArgumentsMap, "guard of transition '${edge.fromLocation} -> ${edge.toLocation}' in '$name'")


            // Now the updates
            for((to, from) in edge.update) {
                // Check where we're writing to
                valid = valid and validateWritingVariables(ParseTreeVariable(to), readableVars, writeableVars, "update equation of transition '${edge.fromLocation} -> ${edge.toLocation}' in '$name'")

                // And where the value is coming from
                valid = valid and validateReadingVariables(from, readableVars, writeableVars, "update equation for '$to' of transition '${edge.fromLocation} -> ${edge.toLocation}' in '$name'")
                valid = valid and from.validate(variableTypes, functionReturnMap, functionArgumentsMap, "update equation for '$to' of transition '${edge.fromLocation} -> ${edge.toLocation}' in '$name'")

                // Updates should return correct types
                if(variableTypes.containsKey(to)) {
                    val assignType = from.getOperationResultType(variableTypes, functionReturnMap)
                    if(variableTypes[to] != assignType && variableTypes[to] != VariableType.ANY) {
                        Logger.error("Incorrect type assigned to update equation for '$to' of transition '${edge.fromLocation} -> ${edge.toLocation}' in '$name'." +
                                " Found '$assignType', expected '${variableTypes[to]}'")
                        valid = false
                    }
                }
            }
        }

        // Finally, let's validate the initialisation code
        // Firstly, the state
        if(!locations.any { it.name == init.state }) {
            Logger.error("Unknown location '${init.state}' used in initialisation of '$name'.")
            valid = false
        }

        // And then the valuations
        for((to, from) in init.valuations) {
            // Check where we're writing to
            valid = valid and validateWritingVariables(ParseTreeVariable(to), readableVars, writeableVars, "initialisation equation in '$name'")

            // And where the value is coming from
            valid = valid and validateReadingVariables(from, readableVars, writeableVars, "initialisation equation for '$to' in '$name'")
            valid = valid and from.validate(variableTypes, functionReturnMap, functionArgumentsMap, "initialisation equation for '$to' in '$name'")

            // Updates should return correct types
            if(variableTypes.containsKey(to)) {
                val assignType = from.getOperationResultType(variableTypes, functionReturnMap)
                if(variableTypes[to] != assignType && variableTypes[to] != VariableType.ANY) {
                    Logger.error("Incorrect type assigned to initialisation equation for '$to' in '$name'." +
                            " Found '$assignType', expected '${variableTypes[to]}'")
                    valid = false
                }
            }
        }

        return valid
    }
}

data class Location(
        var name: String,
        var invariant: ParseTreeItem = Literal("true"),
        var flow: LinkedHashMap<String, ParseTreeItem> = LinkedHashMap(),
        var update: LinkedHashMap<String, ParseTreeItem> = LinkedHashMap()
)

data class Edge(
        var fromLocation: String,
        var toLocation: String,
        var guard: ParseTreeItem = Literal("true"),
        var update: LinkedHashMap<String, ParseTreeItem> = LinkedHashMap()
)

data class Initialisation(
        var state: String,
        var valuations: LinkedHashMap<String, ParseTreeItem> = LinkedHashMap()
)

data class Variable(
        var name: String,
        var type: VariableType,
        var locality: Locality,
        var defaultValue: ParseTreeItem? = null,
        var delayableBy: ParseTreeItem? = null
) {
    fun canBeDelayed(): Boolean = delayableBy != null
}

enum class Locality {
    PARAMETER, EXTERNAL_INPUT, EXTERNAL_OUTPUT, INTERNAL;

    fun getTextualName(): String {
        return when(this) {
            Locality.INTERNAL -> "Internal Variables"
            Locality.EXTERNAL_INPUT -> "Inputs"
            Locality.EXTERNAL_OUTPUT -> "Outputs"
            Locality.PARAMETER -> "Parameters"
        }
    }

    fun getShortName(): String {
        return when(this) {
            Locality.INTERNAL -> "Int"
            Locality.EXTERNAL_INPUT -> "In"
            Locality.EXTERNAL_OUTPUT -> "Out"
            Locality.PARAMETER -> "Param"
        }
    }
}

data class FunctionDefinition(
        var name: String,
        var logic: Program,
        var inputs: ArrayList<VariableDeclaration> = ArrayList(),
        var returnType: VariableType? = null
)
