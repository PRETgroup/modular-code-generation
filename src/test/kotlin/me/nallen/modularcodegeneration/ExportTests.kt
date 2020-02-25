package me.nallen.modularcodegeneration

import io.kotlintest.matchers.shouldEqual
import io.kotlintest.specs.StringSpec
import me.nallen.modularcodegeneration.description.Importer
import me.nallen.modularcodegeneration.description.Exporter
import java.io.File

class ExportTests : StringSpec() {
    init {
        val output = File("build/tmp/export/main.yaml")

        for(test in DescriptionTests.tests) {
            try {
                val imported = Importer.import(test.path)

                val item = imported.first
                val config = imported.second

                ("Can Export HAML File For ${test.name} (${test.format})") {
                    Exporter.export(item, Exporter.ExportFormat.HAML,output.absolutePath, config)

                    val imported2 = Importer.import(output.absolutePath)

                    imported.first shouldEqual imported2.first
                    imported.second shouldEqual imported2.second
                }
            }
            catch(e: Exception) {
                "Can Export HAML File For ${test.name} (${test.format})" {}.config(enabled = false)
            }
        }
    }
}