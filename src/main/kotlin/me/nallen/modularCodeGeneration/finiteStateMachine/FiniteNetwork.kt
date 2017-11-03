package me.nallen.modularCodeGeneration.finiteStateMachine

import me.nallen.modularCodeGeneration.hybridAutomata.AutomataInstance
import me.nallen.modularCodeGeneration.hybridAutomata.HybridNetwork
import me.nallen.modularCodeGeneration.parseTree.ParseTreeItem

/**
 * Created by nathan on 6/06/17.
 */

data class FiniteNetwork(
        var name: String = "Network"
) {
    val definitions = ArrayList<FiniteStateMachine>()
    val instances = LinkedHashMap<String, FiniteInstance>()
    val ioMapping = LinkedHashMap<MachineVariablePair, MachineVariablePair>()

    companion object Factory {
        fun generateFromHybridNetwork(hybridNetwork: HybridNetwork): FiniteNetwork {
            val network = FiniteNetwork()

            network.name = hybridNetwork.name

            for(hybridAutomata in hybridNetwork.definitions) {
                network.addDefinition(FiniteStateMachine.generateFromHybridAutomata(hybridAutomata))
            }

            for((name, instance) in hybridNetwork.instances) {
                network.addInstance(name, FiniteInstance.generateFromAutomataInstance(instance))
            }

            for((input, output) in hybridNetwork.ioMapping) {
                network.addMapping(
                        MachineVariablePair(input.automata, input.variable),
                        MachineVariablePair(output.automata, output.variable)
                )
            }

            return network
        }
    }

    fun addDefinition(fsm: FiniteStateMachine): FiniteNetwork {
        definitions.add(fsm)

        return this
    }

    fun addInstance(name: String, instance: FiniteInstance): FiniteNetwork {
        instances.put(name, instance)

        return this
    }

    fun addMapping(input: MachineVariablePair, output: MachineVariablePair): FiniteNetwork {
        ioMapping.put(input, output)

        return this
    }
}

data class FiniteInstance(
        var machine: String,
        var parameters: Map<String, ParseTreeItem>
) {
    companion object Factory {
        fun generateFromAutomataInstance(instance: AutomataInstance): FiniteInstance {
            val finiteInstance = FiniteInstance(instance.automata, instance.parameters)

            return finiteInstance
        }
    }
}

data class MachineVariablePair(var machine: String, var variable: String)