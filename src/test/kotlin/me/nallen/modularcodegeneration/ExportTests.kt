package me.nallen.modularcodegeneration

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.specs.StringSpec
import me.nallen.modularcodegeneration.codegen.*
import me.nallen.modularcodegeneration.description.Importer
import me.nallen.modularcodegeneration.description.haml.Exporter
import me.nallen.modularcodegeneration.hybridautomata.HybridAutomata
import java.io.File
import java.io.IOException
import java.lang.StringBuilder
import java.util.concurrent.TimeUnit

class ExportTests : StringSpec() {
    init {
        File("examples").list().forEach {
            val folder = File("examples", it)
            if(folder.isDirectory) {
                val main = File(folder, "main.yaml")

                if(main.exists() && main.isFile) {
                    val imported = Importer.import(main.absolutePath)

                    val item = imported.first
                    val config = imported.second

                    ("Can Export HAML File For $it") {
                        Exporter.export(item, "build/tmp/export", config)

                        val imported2 = Importer.import("build/tmp/export/main.yaml")

                        imported.first shouldEqual imported2.first
                        imported.second shouldEqual imported2.second
                    }
                }
            }
        }
    }
}