package me.nallen.modularCodeGeneration.finiteStateMachine

import me.nallen.modularCodeGeneration.hybridAutomata.HybridNetwork

/**
 * Created by nathan on 6/06/17.
 */

data class FiniteNetwork(
        var name: String = "Network"
) {
    val finiteStateMachines = HashMap<String, FiniteStateMachine>()

    val ioMapping = HashMap<MachineVariablePair, MachineVariablePair>()

    companion object Factory {
        fun generateFromHybridNetwork(hybridNetwork: HybridNetwork): FiniteNetwork {
            val network = FiniteNetwork()

            network.name = hybridNetwork.name

            for((name, hybridAutomata) in hybridNetwork.hybridAutomata) {
                network.addFiniteStateMachine(name, FiniteStateMachine.generateFromHybridAutomata(hybridAutomata))
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

    fun addFiniteStateMachine(name: String, fsm: FiniteStateMachine): FiniteNetwork {
        finiteStateMachines.put(name, fsm)
        return this
    }

    fun addMapping(input: MachineVariablePair, output: MachineVariablePair): FiniteNetwork {
        ioMapping.put(input, output)
        return this
    }
}

data class MachineVariablePair(var machine: String, var variable: String)