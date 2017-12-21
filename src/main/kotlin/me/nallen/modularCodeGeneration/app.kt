package me.nallen.modularCodeGeneration

import me.nallen.modularCodeGeneration.codeGen.CodeGenLanguage
import me.nallen.modularCodeGeneration.codeGen.CodeGenManager
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.description.Importer
import me.nallen.modularCodeGeneration.hybridAutomata.HybridNetwork
import kotlin.system.measureTimeMillis

fun main(args: Array<String>) {
    var network = HybridNetwork()
    var config = Configuration()
    var time: Long

    time = measureTimeMillis {
        val imported = Importer.import("examples/heart/main.yaml")
        network = imported.first
        config = imported.second
    }
    println("Import time: $time ms")

    time = measureTimeMillis {
        CodeGenManager.generateForNetwork(network, CodeGenLanguage.C, "Generated", config)
    }
    println("Code Generation time: $time ms")
}