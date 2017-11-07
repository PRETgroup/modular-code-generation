package me.nallen.modularCodeGeneration.hybridAutomata

import me.nallen.modularCodeGeneration.parseTree.ParseTreeItem

/**
 * Created by nathan on 6/06/17.
 */

data class HybridNetwork(
        var name: String = "Network"
) {
    val definitions = ArrayList<HybridAutomata>()
    val instances = LinkedHashMap<String, AutomataInstance>()
    val ioMapping = LinkedHashMap<AutomataVariablePair, AutomataVariablePair>()

    fun addDefinition(
            ha: HybridAutomata
    ): HybridNetwork {
        /*if(definitions.any({it.name == ha.name}))
            throw IllegalArgumentException("Hybrid Automata with name ${ha.name} already exists!")*/

        definitions.add(ha)

        return this
    }

    fun addInstance(
            name: String,
            instance: AutomataInstance
    ): HybridNetwork {
        /*if(instances.containsKey(name))
            throw IllegalArgumentException("Instance with name ${name} already exists!")

        if(!definitions.any({it.name == instance.automata}))
            throw IllegalArgumentException("Instance of undefined definition ${instance.automata}!")

        // Check all parameters
        val ha = definitions.first({it.name == instance.automata})
        val params = ha.continuousVariables.filter({it.locality == Locality.PARAMETER})
        for(param in params) {
            if(!instance.parameters.containsKey(param.name))
                throw IllegalArgumentException("Instance ${name} does not declare value for parameter ${param.name} of ${instance.automata}!")
        }*/

        instances.put(name, instance)

        return this
    }

    fun addMapping(
            to: AutomataVariablePair,
            from: AutomataVariablePair
    ): HybridNetwork {
        /*if(!instances.containsKey(to.automata))
            throw IllegalArgumentException("Unknown instance for 'to' connection ${to.automata}!")

        val toInstance = instances.get(to.automata)!!
        val toHa = definitions.first({it.name == toInstance.automata})

        if(!toHa.continuousVariables.any({it.locality == Locality.EXTERNAL_INPUT && it.name == to.variable}))
            throw IllegalArgumentException("Unknown input for 'to' connection ${to.variable}!")

        if(!instances.containsKey(from.automata))
            throw IllegalArgumentException("Unknown instance for 'from' connection ${from.automata}!")

        val fromInstance = instances.get(from.automata)!!
        val fromHa = definitions.first({it.name == fromInstance.automata})

        if(!fromHa.continuousVariables.any({it.locality == Locality.EXTERNAL_OUTPUT && it.name == from.variable}))
            throw IllegalArgumentException("Unknown input for 'from' connection ${from.variable}!")

        if(ioMapping.containsKey(to)) {
            throw IllegalArgumentException("A previous assignment to ${to.automata}.${to.variable} has already been created!")
        }*/

        ioMapping.put(to, from)

        return this
    }
}

data class AutomataInstance(
        val automata: String,
        val parameters: MutableMap<String, ParseTreeItem> = LinkedHashMap<String, ParseTreeItem>()
)

data class AutomataVariablePair(val automata: String, val variable: String)