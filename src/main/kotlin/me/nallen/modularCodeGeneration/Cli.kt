package me.nallen.modularCodeGeneration

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.mainBody
import me.nallen.modularCodeGeneration.codeGen.CodeGenManager
import me.nallen.modularCodeGeneration.description.Importer

/**
 * The program that gets run for the CLI Application.
 */
fun main(args: Array<String>) = mainBody("piha") {
    CliArgs(ArgParser(args)).run {
        // Read the description and import it
        println("Reading source file '$source'...")
        val (network, config) = Importer.import(source)

        // Generate the code
        println("Generating $language code into directory '$outputDir'")
        CodeGenManager.generate(network, language, outputDir, config)
    }
}