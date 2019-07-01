package me.nallen.modularcodegeneration.description

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import me.nallen.modularcodegeneration.codegen.Configuration
import me.nallen.modularcodegeneration.hybridautomata.*
import java.io.File
import java.io.FileNotFoundException

typealias HamlImporter = me.nallen.modularcodegeneration.description.haml.Importer
typealias CellMLImporter = me.nallen.modularcodegeneration.description.cellml.Importer
typealias CellMLModel = me.nallen.modularcodegeneration.description.cellml.Model

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

            println("In: " + file.absolutePath)

            // Try to open the file
            if(!file.exists() || !file.isFile)
                throw FileNotFoundException("Unable to find the requested file at $path")

            // Now we want to try read the file as a YAML file...
            val yamlMapper = ObjectMapper(YAMLFactory())
            val yamlTree = yamlMapper.registerModule(KotlinModule()).readTree(file)

            // Check if we could actually import it as a YAML file
            if(yamlTree != null) {
                // And if it looks like a HAML file
                if(yamlTree.has("haml")) {
                    return HamlImporter.import(path)
                }
            }

            // Otherwise, let's try it as a CellML file
            val xmlMapper = XmlMapper()
            xmlMapper.configure(MapperFeature.INFER_CREATOR_FROM_CONSTRUCTOR_PROPERTIES,false);
            val cellMLTree = xmlMapper.registerModule(KotlinModule()).readValue(file, CellMLModel::class.java)

            // Check if we could actually import it as an XML file
            if(cellMLTree != null) {
                return CellMLImporter.import(path)
            }

            throw Exception("Unable to determine the provided format for file at $path")
        }
    }
}
