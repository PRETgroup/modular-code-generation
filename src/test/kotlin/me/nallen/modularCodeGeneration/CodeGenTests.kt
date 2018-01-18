package me.nallen.modularCodeGeneration

import io.kotlintest.specs.StringSpec
import me.nallen.modularCodeGeneration.codeGen.*
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
                        var config = imported.second

                        config = config.copy(parametrisationMethod = ParametrisationMethod.COMPILE_TIME)
                        CodeGenManager.generateForNetwork(network, CodeGenLanguage.C, "build/tmp/codegen", config)

                        config = config.copy(parametrisationMethod = ParametrisationMethod.RUN_TIME, logging = Logging())
                        CodeGenManager.generateForNetwork(network, CodeGenLanguage.C, "build/tmp/codegen", config)

                        config = config.copy(maximumInterTransitions = 4, requireOneIntraTransitionPerTick = true)
                        CodeGenManager.generateForNetwork(network, CodeGenLanguage.C, "build/tmp/codegen", config)
                    }
                }
            }
        }
    }

}