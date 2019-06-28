package me.nallen.modularcodegeneration

import me.nallen.modularcodegeneration.codegen.CodeGenLanguage
import me.nallen.modularcodegeneration.codegen.CodeGenManager
import me.nallen.modularcodegeneration.codegen.Configuration
import me.nallen.modularcodegeneration.description.Importer
import me.nallen.modularcodegeneration.description.Exporter
import me.nallen.modularcodegeneration.hybridautomata.HybridItem
import me.nallen.modularcodegeneration.hybridautomata.HybridNetwork
import me.nallen.modularcodegeneration.logging.Logger
import me.nallen.modularcodegeneration.parsetree.ParseTreeItem
import me.nallen.modularcodegeneration.parsetree.evaluateReal
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

/**
 * The main program that gets run when you call 'gradle run'.
 */
fun main() {
    var item: HybridItem = HybridNetwork()
    var config = Configuration()
    var time: Long

    // Times are recorded and output for debugging purposes

    val source = "examples/water_heater/main.yaml"
    val exportFormat = Exporter.ExportFormat.HAML
    val language = CodeGenLanguage.C
    val outputDir = "Generated"

    try {
        // Import from the description
        time = measureTimeMillis {
            val imported = Importer.import(source)
            item = imported.first
            config = imported.second
        }
        println("Import time: $time ms")

        // Export the description (for sanity)
        time = measureTimeMillis {
            Exporter.export(item, exportFormat, outputDir, config)
        }
        println("Export time: $time ms")

        // Generate C code
        /*time = measureTimeMillis {
            CodeGenManager.generate(item, language, outputDir, config)
        }
        println("Code Generation time: $time ms")*/
    }
    catch(e: Exception) {
        Logger.error(e.message ?: "Unexpected Error")
        exitProcess(1)
    }
    catch(e: Error) {
        Logger.error(e.message ?: "Unexpected Error")
        exitProcess(1)
    }
}