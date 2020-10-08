package me.nallen.modularcodegeneration.codegen.c

import com.hubspot.jinjava.Jinjava
import me.nallen.modularcodegeneration.codegen.CodeGenManager
import me.nallen.modularcodegeneration.codegen.Configuration
import me.nallen.modularcodegeneration.hybridautomata.HybridItem
import me.nallen.modularcodegeneration.hybridautomata.HybridNetwork

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
                Utils.createFunctionName(item.name, "Parametrise"),
                config.ccodeSettings.getLoopAnnotation("SIMULATION_TIME / STEP_SIZE") ?: "")

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

        if(config.ccodeSettings.isFixedPoint()) {
            for(loggingField in loggingFields) {
                    loggingField.field = "FROM_FP(" + loggingField.field + ")"
            }
        }

        // Create the context
        val context = RunnableContext(
                config,
                rootItem,
                loggingFields
        )

        val res = jinjava.renderForResult(template, context.map)

        // And generate!
        return res.output
    }

    data class RunnableContext(val map: MutableMap<String, Any?>) {
        var config: Configuration by map
        var item: CodeItem by map
        var loggingFields: List<CLoggingField> by map

        constructor(config: Configuration, item: CodeItem, loggingFields: List<CLoggingField>) : this(
                mutableMapOf(
                        "config" to config,
                        "item" to item,
                        "loggingFields" to loggingFields
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
            var paramFunction: String,
            var loopAnnotation: String
    )
}