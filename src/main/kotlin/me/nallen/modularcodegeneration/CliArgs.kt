package me.nallen.modularcodegeneration

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import com.xenomachina.argparser.SystemExitException
import me.nallen.modularcodegeneration.codegen.CodeGenLanguage

/**
 * An exception used to inform the CLI application that the version should be printed
 */
class ShowVersionException : SystemExitException("Version was requested", 0)

/**
 * Arguments for the CLI Application
 */
class CliArgs(parser: ArgParser) {
    // A flag to print the version of the compiler
    val print_version by parser.option<Unit>("--version",
            help = "show the version of the compiler and exit") {
                throw ShowVersionException()
            }.default(Unit)

    // The (root) source file which will be imported from
    val source by parser.positional("SOURCE", help = "source description file")

    // The language to generate code for
    val language by parser.storing("-l", "--language",
            help = "the language to generate code for") { CodeGenLanguage.valueOf(this) }.default(CodeGenLanguage.C)

    // The output directory where the generated code will go
    val outputDir by parser.storing("-o", "--output",
            help = "the directory to write the generated code to").default("output")

    // Whether or not to "flatten" the network
    val flatten by parser.flagging("-f", "--flatten",
            help = "whether or not to flatten the network for generated code").default(false)

    // A flag to just do validation of the description file
    val only_validation by parser.flagging("-v", "--validate-only",
            help = "whether to perform validation only of the description file, skipping code generation").default(false)
}