package me.nallen.modularCodeGeneration.hybridAutomata

import me.nallen.modularCodeGeneration.parseTree.ParseTreeItem

/**
 * Created by nathan on 6/06/17.
 */

data class HybridNetwork(
        var name: String = "Network"
) {
    val definitions = ArrayList<HybridAutomata>()
    val instances = HashMap<String, AutomataInstance>()
    val ioMapping = HashMap<AutomataVariablePair, AutomataVariablePair>()

    fun addDefinition(
            ha: HybridAutomata
    ): HybridNetwork {
        if(definitions.any({it.name == ha.name}))
            throw IllegalArgumentException("Hybrid Automata with name ${ha.name} already exists!")

        definitions.add(ha)

        return this
    }

    fun addInstance(
            name: String,
            instance: AutomataInstance
    ): HybridNetwork {
        if(instances.containsKey(name))
            throw IllegalArgumentException("Instance with name ${name} already exists!")

        if(!definitions.any({it.name == instance.automata}))
            throw IllegalArgumentException("Instance of undefined definition ${instance.automata}!")

        // Check all parameters
        val ha = definitions.first({it.name == instance.automata})
        val params = ha.continuousVariables.filter({it.locality == Locality.PARAMETER})
        for(param in params) {
            if(!instance.parameters.containsKey(param.name))
                throw IllegalArgumentException("Instance ${name} does not declare value for parameter ${param.name} of ${instance.automata}!")
        }

        instances.put(name, instance)

        return this
    }
}

data class AutomataInstance(
        var automata: String,
        var parameters: HashMap<String, ParseTreeItem>
)

data class AutomataVariablePair(var automata: String, var variable: String)