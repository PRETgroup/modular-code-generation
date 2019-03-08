package me.nallen.modularcodegeneration.hybridautomata

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonValue
import me.nallen.modularcodegeneration.codegen.c.Utils
import me.nallen.modularcodegeneration.logging.Logger
import me.nallen.modularcodegeneration.parsetree.*
import me.nallen.modularcodegeneration.parsetree.Variable
import java.util.*

/**
 * Created by nathan on 6/06/17.
 */

class HybridNetwork(override var name: String = "Network") : HybridItem(){
    val definitions = LinkedHashMap<UUID, HybridItem>()
    val instances = LinkedHashMap<String, AutomataInstance>()
    val instantiates = LinkedHashMap<UUID, AutomataInstantiate>()
    val ioMapping = LinkedHashMap<AutomataVariablePair, ParseTreeItem>()

    @JsonIgnore
    var parent: HybridNetwork? = null

    fun getAllInstances(): Map<String, AutomataInstance> {
        val collection = instances.toMutableMap()

        for((_,instance) in instances) {
            val definition = getDefinitionForInstantiateId(instance.instantiate)
            if(definition != null && definition is HybridNetwork) {
                collection.putAll(definition.getAllInstances())
            }
        }

        return collection
    }

    fun getDefinitionForDefinitionId(definitionId: UUID, searchBelow: Boolean = false): HybridItem? {
        var definition = definitions[definitionId]

        if(definition != null)
            return definition

        definition = parent?.getDefinitionForDefinitionId(definitionId)

        if(definition != null)
            return definition

        for((_, def) in definitions) {
            if(def is HybridNetwork) {
                definition = def.getDefinitionForDefinitionId(definitionId, searchBelow)

                if(definition != null)
                    return definition
            }
        }

        return null
    }

    fun getDefinitionForInstantiateId(instatiateId: UUID, searchBelow: Boolean = false): HybridItem? {
        val instantiate = getInstantiateForInstantiateId(instatiateId, searchBelow)

        if(instantiate != null) {
            return getDefinitionForDefinitionId(instantiate.definition, searchBelow)
        }

        return null
    }

    fun getInstantiateForInstantiateId(instatiateId: UUID, searchBelow: Boolean = false): AutomataInstantiate? {
        var instantiate = instantiates[instatiateId]

        if(instantiate != null)
            return instantiate

        instantiate = parent?.getInstantiateForInstantiateId(instatiateId)

        if(instantiate != null)
            return instantiate

        for((_, def) in definitions) {
            if(def is HybridNetwork) {
                instantiate = def.getInstantiateForInstantiateId(instatiateId, searchBelow)

                if(instantiate != null)
                    return instantiate
            }
        }

        return null
    }

    /**
     * Check if the Hybrid Network is a "flat" network (i.e. only contains Automata) or not
     */
    @JsonIgnore
    fun isFlat(): Boolean {
        // Iterate over every sub-instance
        for((_, instance) in this.instances) {
            // Fetch the definition
            val definition = this.getDefinitionForInstantiateId(instance.instantiate)

            // And if the definition is another network then we know it's not flat
            if(definition != null && definition is HybridNetwork) {
                return false
            }
        }

        // Otherwise if we get here then it is flat!
        return true
    }

    override fun flatten(): HybridItem {
        val flattenedNetwork = HybridNetwork()

        flattenedNetwork.name = this.name
        flattenedNetwork.variables.addAll(this.variables)

        val variableMappings = LinkedHashMap<String, String>()

        val skipVariables = ArrayList<String>()
        val ioVariablesReplace = LinkedHashMap<String, ParseTreeItem>()

        for((key, instance) in this.instances) {
            // Get the instance of the item we want to generate
            val instantiate = this.getInstantiateForInstantiateId(instance.instantiate)
            val definition = this.getDefinitionForInstantiateId(instance.instantiate)
            if(instantiate != null && definition != null) {
                val flattenedDefinition = definition.flatten()

                if(flattenedDefinition is HybridNetwork) {
                    val skippedDefinitionMap = HashMap<UUID, UUID>()
                    val skippedInstantiateMap = HashMap<UUID, UUID>()
                    for(item in flattenedDefinition.definitions) {
                        if(flattenedNetwork.definitions.any { it.value == item.value }) {
                            skippedDefinitionMap[item.key] = flattenedNetwork.definitions
                                    .filter { it.value == item.value }
                                    .keys.first()
                            continue
                        }

                        if(flattenedNetwork.definitions.any { it.value.name == item.value.name })
                            item.value.name = item.value.name + flattenedNetwork.definitions.count { it.value.name.startsWith(item.value.name) }

                        flattenedNetwork.definitions[item.key] = item.value
                    }
                    for(item in flattenedDefinition.instantiates) {
                        if(skippedDefinitionMap.containsKey(item.value.definition)) {
                            skippedInstantiateMap[item.key] = flattenedNetwork.instantiates
                                    .filter { it.value.definition == skippedDefinitionMap[item.value.definition] }
                                    .keys.first()
                            continue
                        }

                        if(flattenedNetwork.instantiates.any { it.value.name == item.value.name })
                            item.value.name = item.value.name + flattenedNetwork.instantiates.count { it.value.name.startsWith(item.value.name) }

                        flattenedNetwork.instantiates[item.key] = item.value
                    }
                    for(item in flattenedDefinition.instances) {
                        item.value.instantiate = skippedInstantiateMap[item.value.instantiate] ?: item.value.instantiate

                        val newKey = Utils.createVariableName(instantiate.name, item.key)
                        flattenedNetwork.instances[newKey] = item.value
                    }
                    for(item in flattenedDefinition.variables) {
                        if(item.locality == Locality.INTERNAL || item.locality == Locality.PARAMETER) {
                            flattenedNetwork.variables.add(item.copy(name = Utils.createVariableName(instantiate.name, item.name), locality = Locality.INTERNAL))
                        }
                        else {
                            skipVariables.add(Utils.createVariableName(instantiate.name, item.name))
                        }
                        variableMappings[instantiate.name + "." + item.name] = Utils.createVariableName(instantiate.name, item.name)
                    }

                    for(item in flattenedDefinition.ioMapping) {
                        var newKey = AutomataVariablePair(Utils.createVariableName(instantiate.name, item.key.automata), item.key.variable)
                        val newValue = item.value.prependVariables(instantiate.name)
                        if(item.key.automata.isNotEmpty()) {
                            val innerInstance = this.instances[item.key.automata]
                            if(innerInstance != null) {
                                val innerDefinition = this.getDefinitionForInstantiateId(instance.instantiate)
                                if(innerDefinition != null) {
                                    if(innerDefinition is HybridNetwork) {
                                        newKey = AutomataVariablePair("", Utils.createVariableName(item.key.automata, item.key.variable))
                                    }
                                }
                            }
                        }
                        else {
                            newKey = AutomataVariablePair("", Utils.createVariableName(instantiate.name, item.key.variable))
                        }

                        if(flattenedDefinition.variables.any { it.name == item.key.getString() && it.locality == Locality.EXTERNAL_OUTPUT }) {
                            ioVariablesReplace[newKey.getString()] = newValue
                        }
                        else {
                            flattenedNetwork.ioMapping[newKey] = newValue
                        }
                    }
                }
                else {
                    flattenedNetwork.definitions[instantiate.definition] = definition
                    flattenedNetwork.instances[key] = instance
                    flattenedNetwork.instantiates[instance.instantiate] = instantiate
                }
            }
        }

        for((key, value) in this.ioMapping) {
            var newKey = key
            val newValue = value.replaceVariables(variableMappings).replaceVariablesWithParseTree(ioVariablesReplace)
            if(key.automata.isNotEmpty()) {
                val instance = this.instances[key.automata]
                if(instance != null) {
                    val definition = this.getDefinitionForInstantiateId(instance.instantiate)
                    if(definition != null) {
                        if(definition is HybridNetwork) {
                            newKey = AutomataVariablePair("", Utils.createVariableName(key.automata, key.variable))
                        }
                    }
                }
            }

            ioVariablesReplace[newKey.getString()] = newValue

            if(!skipVariables.contains(newKey.getString()))
                flattenedNetwork.ioMapping[newKey] = newValue
        }

        for((key, value) in flattenedNetwork.ioMapping) {
            flattenedNetwork.ioMapping[key] = value.replaceVariablesWithParseTree(ioVariablesReplace)
        }

        return flattenedNetwork
    }

    /**
     * Check if this Hybrid Network is valid, this includes things like variable names being correct, instances being
     * able to be instantiated, correct parameters, etc. This can help the user detect errors during the compile stage
     * rather than by analysing the generated code.
     */
    override fun validate(): Boolean {
        // Let's try see if anything isn't valid
        var valid = super.validate()

        val writeableVars = ArrayList<String>()
        val readableVars = ArrayList<String>()

        // We need to keep track of what variables we can write to and read from in this network
        for(variable in this.variables) {
            // Many things can be read from
            if(variable.locality == Locality.EXTERNAL_INPUT || variable.locality == Locality.INTERNAL || variable.locality == Locality.PARAMETER) {
                readableVars.add(variable.name)
            }
            // But fewer can be written to
            if(variable.locality == Locality.EXTERNAL_OUTPUT || variable.locality == Locality.INTERNAL) {
                writeableVars.add(variable.name)
            }
        }

        // We want to keep track of each definition we've validated so that we don't run it multiple times
        val validated = ArrayList<UUID>()

        // Firstly, let's iterate over every sub-instance
        for((name, instance) in this.instances) {
            // Fetch the definition
            val definition = this.getDefinitionForInstantiateId(instance.instantiate)

            if(definition != null) {
                // If we haven't seen this before
                if (!validated.contains(instance.instantiate)) {
                    // Then we want to check if it's valid
                    if (!definition.validate())
                        valid = false

                    validated.add(instance.instantiate)
                }

                // Now we want to check that each parameter is valid
                for((param, _) in instance.parameters) {
                    // Check if there's a matching parameter in the definition
                    if(!definition.variables.any { it.locality == Locality.PARAMETER && it.name == param }) {
                        // If not, then this is just a warning because we can just ignore it
                        Logger.warn("Unable to find parameter '$param' in '${definition.name}' used by '$name'." +
                                " Removing this parameter.")
                        instance.parameters.remove(param)
                    }
                }

                // We also need to keep track of what variables we can write to and read from in this network
                for(variable in definition.variables) {
                    // Inputs of this instance can be written to, while outputs can be read from
                    if(variable.locality == Locality.EXTERNAL_INPUT) {
                        writeableVars.add("$name.${variable.name}")
                    }
                    else if(variable.locality == Locality.EXTERNAL_OUTPUT) {
                        readableVars.add("$name.${variable.name}")
                    }
                }
            }
            else {
                // No definition found, that's an issue but should have been logged earlier
                valid = false
            }
        }

        // Now check for all variables in the I/O Mapping
        for((to, from) in this.ioMapping) {
            // First let's start with where we're writing to
            valid = valid and validateWritingVariables(Variable(to.getString()), readableVars, writeableVars)

            // Now let's validate the right-hand-side
            valid = valid and validateReadingVariables(from, readableVars, writeableVars)
        }

        return valid
    }
}

data class AutomataInstance(
        var instantiate: UUID,
        val parameters: MutableMap<String, ParseTreeItem> = LinkedHashMap()
)

data class AutomataInstantiate(
        val definition: UUID,
        var name: String
)

data class AutomataVariablePair(
        val automata: String,
        val variable: String
) {
    companion object Factory {
        // Method for creating from a String (used in JSON parsing)
        @JsonCreator @JvmStatic
        fun generate(input: String): AutomataVariablePair {
            // We need to check that the "to" part contains a dot, which is used to separate the instantiate from the
            // input variable name
            return if(input.contains(".")) {
                // Split the "to" field up into its Automata and Variable pairs
                AutomataVariablePair(input.substringBeforeLast("."), input.substringAfterLast("."))
            } else {
                // Otherwise this is probably mapping to an output, in which case we leave the automata blank
                AutomataVariablePair("", input)
            }
        }
    }

    @JsonValue
    fun getString(): String {
        if(automata.isBlank())
            return variable

        return "$automata.$variable"
    }
}