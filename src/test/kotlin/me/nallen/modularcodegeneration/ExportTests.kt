package me.nallen.modularcodegeneration

import io.kotlintest.matchers.shouldEqual
import io.kotlintest.specs.StringSpec
import me.nallen.modularcodegeneration.description.Importer
import me.nallen.modularcodegeneration.description.haml.Exporter
import java.io.File

class ExportTests : StringSpec() {
    init {
        File("examples").list().forEach {
            val folder = File("examples", it)
            if(folder.isDirectory) {
                val main = File(folder, "main.yaml")
                val output = File("build/tmp/export/main.yaml")

                if(main.exists() && main.isFile) {
                    ("Can Export HAML File For $it") {
                        val imported = Importer.import(main.absolutePath)

                        val item = imported.first
                        val config = imported.second

                        Exporter.export(item, output.absolutePath, config)

                        println("File: ${output.absolutePath}")

                        val imported2 = Importer.import(output.absolutePath)

                        imported.first shouldEqual imported2.first
                        imported.second shouldEqual imported2.second
                    }
                }
            }
        }
    }
}