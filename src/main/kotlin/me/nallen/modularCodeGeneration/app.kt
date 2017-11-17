package me.nallen.modularCodeGeneration

import me.nallen.modularCodeGeneration.codeGen.CodeGenLanguage
import me.nallen.modularCodeGeneration.codeGen.CodeGenManager
import me.nallen.modularCodeGeneration.description.Importer

fun main(args: Array<String>) {
    val (network, config) = Importer.import("heart.yaml")

    CodeGenManager.generateForNetwork(network, CodeGenLanguage.C, "Generated", config)
}