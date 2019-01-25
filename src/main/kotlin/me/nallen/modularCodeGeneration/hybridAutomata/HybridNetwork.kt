package me.nallen.modularCodeGeneration.hybridAutomata

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonValue
import me.nallen.modularCodeGeneration.codeGen.c.Utils
import me.nallen.modularCodeGeneration.parseTree.ParseTreeItem
import me.nallen.modularCodeGeneration.parseTree.prependVariables
import me.nallen.modularCodeGeneration.parseTree.replaceVariables
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

    /*fun addDefinition(
            ha: HybridAutomata
    ): HybridNetwork {
        /*if(definitions.any({it.name == ha.name}))
            throw IllegalArgumentException("Hybrid Automata with name ${ha.name} already exists!")*/

        definitions.add(ha)

        return this
    }*/

    /*fun addInstance(
            name: String,
            instantiate: AutomataInstance
    ): HybridNetwork {
        /*if(instances.containsKey(name))
            throw IllegalArgumentException("Instance with name ${name} already exists!")

        if(!definitions.any({it.name == instantiate.automata}))
            throw IllegalArgumentException("Instance of undefined definition ${instantiate.automata}!")

        // Check all parameters
        val ha = definitions.first({it.name == instantiate.automata})
        val params = ha.continuousVariables.filter({it.locality == Locality.PARAMETER})
        for(param in params) {
            if(!instantiate.parameters.containsKey(param.name))
                throw IllegalArgumentException("Instance ${name} does not declare value for parameter ${param.name} of ${instantiate.automata}!")
        }*/

        instances[name] = instantiate

        return this
    }*/

    /*fun addMapping(
            to: AutomataVariablePair,
            from: ParseTreeItem
    ): HybridNetwork {
        /*if(!instances.containsKey(to.automata))
            throw IllegalArgumentException("Unknown instantiate for 'to' connection ${to.automata}!")

        val toInstance = instances.get(to.automata)!!
        val toHa = definitions.first({it.name == toInstance.automata})

        if(!toHa.continuousVariables.any({it.locality == Locality.EXTERNAL_INPUT && it.name == to.variable}))
            throw IllegalArgumentException("Unknown input for 'to' connection ${to.variable}!")

        if(!instances.containsKey(from.automata))
            throw IllegalArgumentException("Unknown instantiate for 'from' connection ${from.automata}!")

        val fromInstance = instances.get(from.automata)!!
        val fromHa = definitions.first({it.name == fromInstance.automata})

        if(!fromHa.continuousVariables.any({it.locality == Locality.EXTERNAL_OUTPUT && it.name == from.variable}))
            throw IllegalArgumentException("Unknown input for 'from' connection ${from.variable}!")

        if(ioMapping.containsKey(to)) {
            throw IllegalArgumentException("A previous assignment to ${to.automata}.${to.variable} has already been created!")
        }*/

        ioMapping[to] = from

        return this
    }*/

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

    override fun flatten(): HybridItem {
        val flattenedNetwork = HybridNetwork()

        flattenedNetwork.name = this.name
        flattenedNetwork.variables.addAll(this.variables)

        val variableMappings = LinkedHashMap<String, String>()

        for((key, instance) in this.instances) {
            // Get the instance of the item we want to generate
            val instantiate = this.getInstantiateForInstantiateId(instance.instantiate)
            val definition = this.getDefinitionForInstantiateId(instance.instantiate)
            if(instantiate != null && definition != null) {
                val flattenedDefinition = definition.flatten()

                if(flattenedDefinition is HybridNetwork) {
                    for(item in flattenedDefinition.definitions) {
                        flattenedNetwork.definitions[item.key] = item.value
                    }
                    for(item in flattenedDefinition.instances) {
                        val newKey = Utils.createVariableName(instantiate.name, item.key)
                        flattenedNetwork.instances[newKey] = item.value
                    }
                    for(item in flattenedDefinition.instantiates) {
                        flattenedNetwork.instantiates[item.key] = item.value
                    }
                    for(item in flattenedDefinition.variables) {
                        flattenedNetwork.variables.add(item.copy(name = Utils.createVariableName(instantiate.name, item.name), locality = Locality.INTERNAL))
                        variableMappings[instantiate.name + "." + item.name] = Utils.createVariableName(instantiate.name, item.name)
                    }

                    for(item in flattenedDefinition.ioMapping) {
                        var newKey = AutomataVariablePair(Utils.createVariableName(instantiate.name, item.key.automata), item.key.variable)
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

                        flattenedNetwork.ioMapping[newKey] = item.value.prependVariables(instantiate.name)
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

            flattenedNetwork.ioMapping[newKey] = value.replaceVariables(variableMappings)
        }

        return flattenedNetwork
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