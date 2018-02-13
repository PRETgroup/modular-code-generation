package me.nallen.modularCodeGeneration.codeGen.c

import com.hubspot.jinjava.Jinjava
import me.nallen.modularCodeGeneration.codeGen.CodeGenManager
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.hybridAutomata.HybridItem
import me.nallen.modularCodeGeneration.hybridAutomata.HybridNetwork

/**
 * The class that contains methods to do with the generation of the main runnable file for the system
 */
object RunnableGenerator {
    /**
     * Generates a string that represents the main runnable file for the system.
     */
    fun generate(item: HybridItem, config: Configuration = Configuration()): String {
        val jinjava = Jinjava()

        val template = this::class.java.classLoader.getResource("templates/c/runnable.c").readText()

        // Generate data about the root item
        val rootItem = CodeItem("", Utils.createTypeName(item.name), Utils.createVariableName(item.name, "data"),
                Utils.createFunctionName(item.name, "Run"), Utils.createFunctionName(item.name, "Init"),
                Utils.createFunctionName(item.name, "Parametrise"))

        // And add it's include path
        rootItem.include = if(item is HybridNetwork)
            "${Utils.createFolderName(item.name, "Network")}/${Utils.createFileName(item.name)}.h"
        else
            "${Utils.createFileName(item.name)}.h"

        // Get the logging fields from the item
        val toLog = CodeGenManager.collectFieldsToLog(item, config)

        // And create our format
        val loggingFields = ArrayList<CLoggingField>()
        for ((variable, type) in toLog) {
            loggingFields.add(
                    CLoggingField(
                            "${item.name}.$variable",
                            "${Utils.createVariableName(item.name, "data")}.${Utils.createVariableName(variable)}",
                            Utils.generatePrintfType(type)
                    )
            )
        }

        // Create the context
        val context = RunnableContext(
                rootItem,
                loggingFields,
                config
        )

        val res = jinjava.renderForResult(template, context.map)

        // And generate!
        return res.output
    }

    data class RunnableContext(val map: MutableMap<String, Any?>) {
        var item: CodeItem by map
        var loggingFields: List<CLoggingField> by map
        var config: Configuration by map

        constructor(item: CodeItem, loggingFields: List<CLoggingField>, config: Configuration) : this(
                mutableMapOf(
                        "item" to item,
                        "loggingFields" to loggingFields,
                        "config" to config
                )
        )
    }

    data class CLoggingField(
            var name: String,
            var field: String,
            var formatString: String
    )

    data class CodeItem(
            var include: String,
            var type: String,
            var variable: String,
            var runFunction: String,
            var initFunction: String,
            var paramFunction: String
    )
}