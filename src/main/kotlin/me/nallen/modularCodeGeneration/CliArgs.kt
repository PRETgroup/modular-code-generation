package me.nallen.modularCodeGeneration

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import me.nallen.modularCodeGeneration.codeGen.CodeGenLanguage

class CliArgs(parser: ArgParser) {
    val source by parser.positional("SOURCE", help = "source description file")

    val language by parser.storing("-l", "--language",
            help = "the language to generate code for") { CodeGenLanguage.valueOf(this) }.default(CodeGenLanguage.C)

    val outputDir by parser.storing("-o", "--output",
            help = "the directory to write the generated code to").default("output")
}