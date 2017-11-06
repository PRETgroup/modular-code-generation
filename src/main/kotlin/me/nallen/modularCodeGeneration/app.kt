package me.nallen.modularCodeGeneration

import me.nallen.modularCodeGeneration.codeGen.CodeGenLanguage
import me.nallen.modularCodeGeneration.codeGen.CodeGenManager
import me.nallen.modularCodeGeneration.description.Importer
import me.nallen.modularCodeGeneration.finiteStateMachine.FiniteNetwork

fun main(args: Array<String>) {
    val network = Importer.import("heart.yaml")

    val fsmNetwork = FiniteNetwork.generateFromHybridNetwork(network)

    CodeGenManager.generateForNetwork(fsmNetwork, CodeGenLanguage.C, "Generated")
}