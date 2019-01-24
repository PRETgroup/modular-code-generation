package me.nallen.modularCodeGeneration

import me.nallen.modularCodeGeneration.codeGen.CodeGenLanguage
import me.nallen.modularCodeGeneration.codeGen.CodeGenManager
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.description.Importer
import me.nallen.modularCodeGeneration.hybridAutomata.HybridItem
import me.nallen.modularCodeGeneration.hybridAutomata.HybridNetwork
import kotlin.system.measureTimeMillis

/**
 * The main program that gets run when you call 'gradle run'.
 */
fun main(args: Array<String>) {
    var item: HybridItem = HybridNetwork()
    var config = Configuration()
    var time: Long

    // Times are recorded and output for debugging purposes

    // Import from the description
    time = measureTimeMillis {
        val imported = Importer.import("examples/water_heater/main.yaml")
        item = imported.first
        config = imported.second
    }
    println("Import time: $time ms")

    item = item.flatten()

    // Generate C code
    time = measureTimeMillis {
        CodeGenManager.generate(item, CodeGenLanguage.C, "Generated", config)
    }
    println("Code Generation time: $time ms")
}