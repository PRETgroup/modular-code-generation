package me.nallen.modularcodegeneration.description

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import me.nallen.modularcodegeneration.codegen.Configuration
import me.nallen.modularcodegeneration.hybridautomata.*
import java.io.File
import java.io.FileNotFoundException
import java.net.URL

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
            val (contents, _) = loadFromPath(path)

            // Now we want to try read the file as a YAML file...
            val yamlMapper = YAMLMapper().registerKotlinModule()
            val yamlTree = yamlMapper.readTree(contents)

            // Check if we could actually import it as a YAML file
            if(yamlTree != null) {
                // And if it looks like a HAML file
                if(yamlTree.has("system")) {
                    return HamlImporter.import(path)
                }
            }

            // Otherwise, let's try it as a CellML file
            val filteredContents = contents.replace(Regex("<documentation.*</documentation>", RegexOption.DOT_MATCHES_ALL), "")

            val xmlMapper = XmlMapper.builder()
                .configure(MapperFeature.INFER_CREATOR_FROM_CONSTRUCTOR_PROPERTIES, false)
                .build().registerKotlinModule()
            val cellMLTree = xmlMapper.readValue(filteredContents, CellMLModel::class.java)

            // Check if we could actually import it as an XML file
            if(cellMLTree != null) {
                return CellMLImporter.import(path)
            }

            throw Exception("Unable to determine the provided format for file at $path")
        }

        fun loadFromPath(path: String): Pair<String, Boolean> {
            try {
                val url = URL(path)

                return Pair(url.readText(), true)
            }
            catch(e: Exception) {}

            val file = File(path)

            if(!file.exists() || !file.isFile)
                throw FileNotFoundException("Unable to find the requested file at $path")

            return Pair(file.readText(), false)
        }
    }
}
