package me.nallen.modularcodegeneration

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.mainBody
import me.nallen.modularcodegeneration.codegen.CodeGenManager
import me.nallen.modularcodegeneration.description.Importer

/**
 * The program that gets run for the CLI Application.
 */
fun main(args: Array<String>) = mainBody("piha") {
    CliArgs(ArgParser(args)).run {
        // Read the description and import it
        println("Reading source file '$source'...")
        var (network, config) = Importer.import(source)

        if(flatten)
            network = network.flatten()

        // Generate the code
        println("Generating $language code into directory '$outputDir'")
        CodeGenManager.generate(network, language, outputDir, config)
    }
}