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
        fun export(item: HybridItem, format: ExportFormat, file: String, config: Configuration = Configuration()) {
            val outputFile = File(file)

            // If the desired output directory already exists and is a file, then we stop!
            if(outputFile.exists() && !outputFile.isFile)
                throw IllegalArgumentException("Desired output file $file already exists and is not a file!")

            // Depending on the format, we want to call a different generator.
            when(format) {
                ExportFormat.HAML -> HamlExporter.export(item, file, config)
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
