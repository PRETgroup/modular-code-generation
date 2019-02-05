package me.nallen.modularCodeGeneration

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec
import me.nallen.modularCodeGeneration.codeGen.*
import me.nallen.modularCodeGeneration.description.Importer
import me.nallen.modularCodeGeneration.hybridAutomata.HybridAutomata
import java.io.File
import java.io.IOException
import java.lang.StringBuilder
import java.util.concurrent.TimeUnit

class CodeGenTests : StringSpec() {
    init {
        val canMake = "make".runCommand(File("build"), ProcessBuilder.Redirect.PIPE) != 127

        File("examples").list().forEach {
            val folder = File("examples", it)
            if(folder.isDirectory) {
                val main = File(folder, "main.yaml")

                if(main.exists() && main.isFile) {
                    val imported = Importer.import(main.absolutePath)

                    val network = imported.first
                    var config = imported.second

                    ("Can Generate" + if(canMake) { " and Compile" } else { "" } + " Compile-Time C Code For $it") {
                        config = config.copy(parametrisationMethod = ParametrisationMethod.COMPILE_TIME)
                        CodeGenManager.generate(network, CodeGenLanguage.C, "build/tmp/codegen", config)
                        if (canMake) {
                            "make".runCommand(File("build/tmp/codegen")) shouldBe 0
                        }
                    }

                    ("Can Generate" + if(canMake) { " and Compile" } else { "" } + " Run-Time C Code For $it") {
                        config = config.copy(parametrisationMethod = ParametrisationMethod.RUN_TIME, logging = Logging())
                        CodeGenManager.generate(network, CodeGenLanguage.C, "build/tmp/codegen", config)
                        if (canMake) {
                            "make".runCommand(File("build/tmp/codegen")) shouldBe 0
                        }
                    }

                    ("Can Generate" + if(canMake) { " and Compile" } else { "" } + " full HA semantics C Code For $it") {
                        config = config.copy(maximumInterTransitions = 4, requireOneIntraTransitionPerTick = true)
                        CodeGenManager.generate(network, CodeGenLanguage.C, "build/tmp/codegen", config)
                        if (canMake) {
                            "make".runCommand(File("build/tmp/codegen")) shouldBe 0
                        }
                    }

                    ("Can Generate" + if(canMake) { " and Compile" } else { "" } + " partial HA semantics C Code For $it") {
                        config = config.copy(maximumInterTransitions = 4, requireOneIntraTransitionPerTick = false)
                        CodeGenManager.generate(network, CodeGenLanguage.C, "build/tmp/codegen", config)
                        if(canMake) {
                            "make".runCommand(File("build/tmp/codegen")) shouldBe 0
                        }
                    }
                }
            }
        }

        if(!canMake) {
            "Can Compile C Code" {}.config(enabled = false)
        }

        val canGhdl = "ghdl -v".runCommand(File("build"), ProcessBuilder.Redirect.PIPE) != 127

        File("examples").list().forEach {
            val folder = File("examples", it)
            if(folder.isDirectory) {
                val main = File(folder, "main.yaml")

                if(main.exists() && main.isFile) {
                    if(it != "heart") {
                        val imported = Importer.import(main.absolutePath)

                        val network = imported.first
                        var config = imported.second

                        val levels = if(network is HybridAutomata) { 1 } else { 2 }

                        ("Can Generate" + if(canGhdl) { " and Synthesise" } else { "" } + " Compile-Time VHDL Code For $it") {
                            config = config.copy(parametrisationMethod = ParametrisationMethod.COMPILE_TIME)
                            CodeGenManager.generate(network, CodeGenLanguage.VHDL, "build/tmp/codegen", config)
                            if (canGhdl) {
                                makeGhdl(File("build/tmp/codegen"), levels) shouldBe 0
                            }
                        }

                        ("Can Generate" + if(canGhdl) { " and Synthesise" } else { "" } + " Run-Time VHDL Code For $it") {
                            config = config.copy(parametrisationMethod = ParametrisationMethod.RUN_TIME)
                            CodeGenManager.generate(network, CodeGenLanguage.VHDL, "build/tmp/codegen", config)
                            if(canGhdl) {
                                makeGhdl(File("build/tmp/codegen"), levels) shouldBe 0
                            }
                        }
                    }
                }
            }
        }

        if(!canGhdl) {
            "Can Synthesise VHDL Code" {}.config(enabled = false)
        }
    }

    private fun String.runCommand(workingDir: File, redirect: ProcessBuilder.Redirect = ProcessBuilder.Redirect.INHERIT): Int {
        return this.split("\\s".toRegex()).toTypedArray().runCommand(workingDir, redirect)
    }

    private fun Array<String>.runCommand(workingDir: File, redirect: ProcessBuilder.Redirect = ProcessBuilder.Redirect.INHERIT): Int {
        return try {
            val proc = ProcessBuilder(*this)
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

    private fun makeGhdl(workingDir: File, levels: Int = 1, redirect: ProcessBuilder.Redirect = ProcessBuilder.Redirect.INHERIT): Int {
        val builder = StringBuilder("")
        for(i in 1 .. levels) {
            arrayOf("bash", "-c", "ghdl -i ${builder.toString()}*.vhdl").runCommand(workingDir, redirect)
            builder.append("*/")
        }
        return "ghdl -m system".runCommand(workingDir, redirect)
    }

}