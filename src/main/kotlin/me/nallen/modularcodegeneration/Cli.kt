package me.nallen.modularcodegeneration

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.mainBody
import me.nallen.modularcodegeneration.codegen.CodeGenManager
import me.nallen.modularcodegeneration.description.Importer
import me.nallen.modularcodegeneration.logging.Logger
import kotlin.system.exitProcess

/**
 * The program that gets run for the CLI Application.
 */
fun main(args: Array<String>) = mainBody("piha") {
    CliArgs(ArgParser(args)).run {
        try {
            // Read the description and import it
            var (network, config) = Importer.import(source)

            if (flatten)
                network = network.flatten()

            if(!only_validation) {
                // Generate the code
                CodeGenManager.generate(network, language, outputDir, config)
            }
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
}