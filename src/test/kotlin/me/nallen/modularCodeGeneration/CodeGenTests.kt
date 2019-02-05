package me.nallen.modularCodeGeneration

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec
import me.nallen.modularCodeGeneration.codeGen.*
import me.nallen.modularCodeGeneration.description.Importer
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class CodeGenTests : StringSpec() {
    init {
        val canMake = "make".runCommand(File("build"), ProcessBuilder.Redirect.PIPE) != 127

        File("examples").list().forEach {
            val folder = File("examples", it)
            if(folder.isDirectory) {
                val main = File(folder, "main.yaml")

                if(main.exists() && main.isFile) {

                    ("Can Generate" + if(canMake) { " and Compile" } else { "" } + " C Code For $it") {
                        val imported = Importer.import(main.absolutePath)

                        val network = imported.first
                        var config = imported.second

                        config = config.copy(parametrisationMethod = ParametrisationMethod.COMPILE_TIME)
                        CodeGenManager.generate(network, CodeGenLanguage.C, "build/tmp/codegen", config)
                        if(canMake) {
                            "make".runCommand(File("build/tmp/codegen")) shouldBe 0
                        }

                        config = config.copy(indentSize = -1, parametrisationMethod = ParametrisationMethod.RUN_TIME, logging = Logging())
                        CodeGenManager.generate(network, CodeGenLanguage.C, "build/tmp/codegen", config)
                        if(canMake) {
                            "make".runCommand(File("build/tmp/codegen")) shouldBe 0
                        }

                        config = config.copy(maximumInterTransitions = 4, requireOneIntraTransitionPerTick = true)
                        CodeGenManager.generate(network, CodeGenLanguage.C, "build/tmp/codegen", config)
                        if(canMake) {
                            "make".runCommand(File("build/tmp/codegen")) shouldBe 0
                        }

                        config = config.copy(maximumInterTransitions = 4, requireOneIntraTransitionPerTick = false)
                        CodeGenManager.generate(network, CodeGenLanguage.C, "build/tmp/codegen", config)
                        if(canMake) {
                            "make".runCommand(File("build/tmp/codegen")) shouldBe 0
                        }
                    }

                    ("Can Generate" + if(canMake) { " and Compile" } else { "" } + " C Code For $it when flattened") {
                        val imported = Importer.import(main.absolutePath)

                        val network = imported.first.flatten()
                        var config = imported.second

                        config = config.copy(parametrisationMethod = ParametrisationMethod.COMPILE_TIME)
                        CodeGenManager.generate(network, CodeGenLanguage.C, "build/tmp/codegen", config)
                        if(canMake) {
                            "make".runCommand(File("build/tmp/codegen")) shouldBe 0
                        }

                        config = config.copy(parametrisationMethod = ParametrisationMethod.RUN_TIME)
                        CodeGenManager.generate(network, CodeGenLanguage.C, "build/tmp/codegen", config)
                        if(canMake) {
                            "make".runCommand(File("build/tmp/codegen")) shouldBe 0
                        }
                    }

                    if(!canMake) {
                        "Can Compile C Code For $it" {

                        }.config(enabled = false)

                        "Can Compile C Code For $it when flattened" {

                        }.config(enabled = false)
                    }
                }
            }
        }

        val canGhdl = "ghdl -v".runCommand(File("build"), ProcessBuilder.Redirect.PIPE) != 127

        File("examples").list().forEach {
            val folder = File("examples", it)
            if(folder.isDirectory) {
                val main = File(folder, "main.yaml")

                if(main.exists() && main.isFile) {
                    if(it != "heart") {
                        ("Can Generate" + if(canGhdl) { " and Synthesise" } else { "" } + " VHDL Code For $it") {
                            val imported = Importer.import(main.absolutePath)

                            val network = imported.first
                            var config = imported.second

                            config = config.copy(parametrisationMethod = ParametrisationMethod.COMPILE_TIME)
                            CodeGenManager.generate(network, CodeGenLanguage.VHDL, "build/tmp/codegen", config)
                            if(canGhdl) {
                                makeGhdl(File("build/tmp/codegen")) shouldBe 0
                            }

                            config = config.copy(parametrisationMethod = ParametrisationMethod.RUN_TIME)
                            CodeGenManager.generate(network, CodeGenLanguage.VHDL, "build/tmp/codegen", config)
                            if(canGhdl) {
                                makeGhdl(File("build/tmp/codegen")) shouldBe 0
                            }
                        }

                        ("Can Generate" + if(canGhdl) { " and Synthesise" } else { "" } + " VHDL Code For $it when flattened") {
                            val imported = Importer.import(main.absolutePath)

                            val network = imported.first.flatten()
                            var config = imported.second

                            config = config.copy(parametrisationMethod = ParametrisationMethod.COMPILE_TIME)
                            CodeGenManager.generate(network, CodeGenLanguage.VHDL, "build/tmp/codegen", config)
                            if(canGhdl) {
                                makeGhdl(File("build/tmp/codegen")) shouldBe 0
                            }

                            config = config.copy(parametrisationMethod = ParametrisationMethod.RUN_TIME)
                            CodeGenManager.generate(network, CodeGenLanguage.VHDL, "build/tmp/codegen", config)
                            if(canGhdl) {
                                makeGhdl(File("build/tmp/codegen")) shouldBe 0
                            }
                        }

                        if(!canGhdl) {
                            "Can Synthesise VHDL Code For $it" {

                            }.config(enabled = false)

                            "Can Synthesise VHDL Code For $it when flattened" {

                            }.config(enabled = false)
                        }
                    }
                }
            }
        }
    }

    private fun String.runCommand(workingDir: File, redirect: ProcessBuilder.Redirect = ProcessBuilder.Redirect.INHERIT): Int {
        return try {
            val proc = ProcessBuilder(this)
                    .directory(workingDir)
                    .redirectOutput(redirect)
                    .redirectError(redirect)
                    .start()

            proc.waitFor(60, TimeUnit.MINUTES)
            proc.exitValue()
        }
        catch(ex: IOException) {
            127
        }
    }

    private fun makeGhdl(workingDir: File, redirect: ProcessBuilder.Redirect = ProcessBuilder.Redirect.INHERIT): Int {
        "/bin/bash -c 'ghdl -i *.vhdl'".runCommand(workingDir, redirect)
        "/bin/bash -c 'ghdl -i */*.vhdl'".runCommand(workingDir, redirect)
        return "ghdl -m system".runCommand(workingDir, redirect)
    }

}