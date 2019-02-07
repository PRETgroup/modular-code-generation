package me.nallen.modularcodegeneration

import me.nallen.modularcodegeneration.codegen.CodeGenLanguage
import me.nallen.modularcodegeneration.codegen.CodeGenManager
import me.nallen.modularcodegeneration.codegen.Configuration
import me.nallen.modularcodegeneration.description.Importer
import me.nallen.modularcodegeneration.hybridautomata.HybridItem
import me.nallen.modularcodegeneration.hybridautomata.HybridNetwork
import kotlin.system.measureTimeMillis

/**
 * The main program that gets run when you call 'gradle run'.
 */
fun main() {
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

    // Generate C code
    time = measureTimeMillis {
        CodeGenManager.generate(item, CodeGenLanguage.C, "Generated", config)
    }
    println("Code Generation time: $time ms")
}