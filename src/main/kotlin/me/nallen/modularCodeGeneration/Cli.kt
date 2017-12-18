package me.nallen.modularCodeGeneration

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.mainBody
import me.nallen.modularCodeGeneration.codeGen.CodeGenManager
import me.nallen.modularCodeGeneration.description.Importer

fun main(args: Array<String>) = mainBody("piha") {
    CliArgs(ArgParser(args)).run {
        println("Reading source file '$source'...")

        val (network, config) = Importer.import(source)

        println("Generating $language code into directory '$outputDir'")
        CodeGenManager.generateForNetwork(network, language, outputDir, config)
    }
}