package me.nallen.modularCodeGeneration

import io.kotlintest.specs.StringSpec
import me.nallen.modularCodeGeneration.codeGen.CodeGenLanguage
import me.nallen.modularCodeGeneration.codeGen.CodeGenManager
import me.nallen.modularCodeGeneration.description.Importer
import java.io.File

class CodeGenTests : StringSpec() {
    init {
        File("examples").list().forEach {
            val folder = File("examples", it)
            if(folder.isDirectory) {
                val main = File(folder, "main.yaml")

                if(main.exists() && main.isFile) {
                    ("Can Generate Code For " + it) {
                        val imported = Importer.import(main.absolutePath)

                        val network = imported.first
                        val config = imported.second

                        CodeGenManager.generateForNetwork(network, CodeGenLanguage.C, "build/tmp/codegen", config)
                    }
                }
            }
        }
    }

}