package me.nallen.modularCodeGeneration.hybridAutomata

/**
 * Created by nathan on 6/06/17.
 */

data class HybridNetwork(
        var name: String = "Network"
) {
    val hybridAutomata = HashMap<String, HybridAutomata>()

    val ioMapping = HashMap<AutomataVariablePair, AutomataVariablePair>()

}

data class AutomataVariablePair(var automata: String, var variable: String)