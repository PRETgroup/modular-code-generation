package me.nallen.modularCodeGeneration.description

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.description.cellml.Schema
import me.nallen.modularCodeGeneration.hybridAutomata.*
import java.io.File

/**
 * An Importer which is capable of reading in a HAML Document specification and creating the associated Hybrid Item
 * as described in the document.
 */
class Importer {
    companion object Factory {
        /**
         * Imports the provided document at the specified path and converts it to a Hybrid Item. The format of the input
         * file is automatically detected by this method.
         *
         * Configuration settings are also parsed and returned as a separate Configuration object.
         */
        fun import(path: String): Pair<HybridItem, Configuration> {
            val file = File(path)

            // Try to open the file
            if(!file.exists() || !file.isFile)
                throw Exception("Whoops")

            // Now we want to read the file as a YAML file...
            val mapper = ObjectMapper(YAMLFactory())
            val schema = mapper.registerModule(KotlinModule()).readTree(file)

            if(schema != null) {
                if(schema.has("haml")) {
                    return me.nallen.modularCodeGeneration.description.haml.Importer.import(path)
                }
            }

            throw Exception("Unknown format provided for file")
        }
    }
}