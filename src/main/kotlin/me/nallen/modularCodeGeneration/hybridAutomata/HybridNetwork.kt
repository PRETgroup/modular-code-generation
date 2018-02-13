package me.nallen.modularCodeGeneration.hybridAutomata

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonValue
import me.nallen.modularCodeGeneration.parseTree.ParseTreeItem
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