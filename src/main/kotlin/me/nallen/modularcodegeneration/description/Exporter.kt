package me.nallen.modularcodegeneration.description

import me.nallen.modularcodegeneration.codegen.Configuration
import me.nallen.modularcodegeneration.hybridautomata.*
import java.io.File

typealias HamlExporter = me.nallen.modularcodegeneration.description.haml.Exporter

/**
 * An Exporter which is capable of outputting a HybridItem as some various types
 */
class Exporter {
    companion object Factory {
        /**
         * Exports the provided item to the specified path in the specified format.
         *
         * Configuration settings are also included in the output file, where appropriate.
         */
        fun export(item: HybridItem, format: ExportFormat, dir: String, config: Configuration = Configuration()) {
            val outputDir = File(dir)

            // If the desired output directory already exists and is a file, then we stop!
            if(outputDir.exists() && !outputDir.isDirectory)
                throw IllegalArgumentException("Desired output directory $dir is not a directory!")

            // Easiest way to clear the directory is to recursively delete, then recreate
            outputDir.deleteRecursively()
            outputDir.mkdirs()

            // Depending on the format, we want to call a different generator.
            when(format) {
                ExportFormat.HAML -> HamlExporter.export(item, dir, config)
            }
        }
    }


    /**
     * An enum that represents the possible formats that files can be exported in
     */
    enum class ExportFormat {
        HAML
    }
}
