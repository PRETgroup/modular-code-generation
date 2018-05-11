package me.nallen.modularCodeGeneration.description.cellml

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.hybridAutomata.*
import java.io.File


/**
 * An Importer which is capable of reading in a CellML Document specification and creating the associated Hybrid Item
 * as described in the document.
 */
class Importer {
    companion object Factory {
        /**
         * Imports the CellML document at the specified path and converts it to a Hybrid Item.
         */
        fun import(path: String): Pair<HybridItem, Configuration> {
            val file = File(path)

            // Try to open the file
            if(!file.exists() || !file.isFile)
                throw Exception("Whoops")

            val xmlMapper = XmlMapper()
            xmlMapper.configure(MapperFeature.INFER_CREATOR_FROM_CONSTRUCTOR_PROPERTIES,false);
            val cellMLTree: Model? = xmlMapper.registerModule(KotlinModule()).readValue(file, Model::class.java)

            // Check if we could actually import it as an XML file
            if(cellMLTree != null) {
                println(cellMLTree.components!![1].maths)
                println()
            }

            return Pair(HybridNetwork(), Configuration())
        }
    }
}
