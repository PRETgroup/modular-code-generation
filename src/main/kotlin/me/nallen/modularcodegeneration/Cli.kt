package me.nallen.modularcodegeneration

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.mainBody
import com.xenomachina.argparser.ShowHelpException
import com.xenomachina.argparser.SystemExitException
import me.nallen.modularcodegeneration.codegen.CodeGenManager
import me.nallen.modularcodegeneration.description.Importer
import me.nallen.modularcodegeneration.logging.Logger
import kotlin.system.exitProcess
import java.util.Properties

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
        catch(e: ShowVersionException) {
            val stream = object {}.javaClass.classLoader.getResourceAsStream("version.properties")
            var version: String? = null
            if(stream != null) {
                val versionProperties = Properties()
                versionProperties.load(stream)
                version = versionProperties.getProperty("version") ?: null
            }
            
            if(version != null)
                Logger.info("Version: " + version)
            else
                Logger.error("Unable to find version")
            
            exitProcess(e.returnCode)
        }
        catch(e: ShowHelpException) {
            throw e
        }
        catch(e: SystemExitException) {
            Logger.error(e.message ?: "Unexpected Error")
            exitProcess(e.returnCode)
        }
    }
}