package me.nallen.modularCodeGeneration.description

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.description.cellml.Units
import me.nallen.modularCodeGeneration.hybridAutomata.*
import java.io.File

/**
 * An Importer which is capable of reading in a specification and creating the associated Hybrid Item as described in
 * the document.
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

            // Now we want to try read the file as a YAML file...
            val yamlMapper = ObjectMapper(YAMLFactory())
            val yamlTree = yamlMapper.registerModule(KotlinModule()).readTree(file)

            // Check if we could actually import it as a YAML file
            if(yamlTree != null) {
                // And if it looks like a HAML file
                if(yamlTree.has("haml")) {
                    return me.nallen.modularCodeGeneration.description.haml.Importer.import(path)
                }
            }

            // Otherwise, let's try it as a CellML file
            val xmlMapper = XmlMapper()
            xmlMapper.configure(MapperFeature.INFER_CREATOR_FROM_CONSTRUCTOR_PROPERTIES,false);
            val cellMLTree = xmlMapper.registerModule(KotlinModule()).readValue(file, me.nallen.modularCodeGeneration.description.cellml.Model::class.java)

            // Check if we could actually import it as an XML file
            if(cellMLTree != null) {
                return me.nallen.modularCodeGeneration.description.cellml.Importer.import(path)
            }

            throw Exception("Unknown format provided for file")
        }
    }
}