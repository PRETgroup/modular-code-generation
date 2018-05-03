package me.nallen.modularCodeGeneration

import io.kotlintest.specs.StringSpec
import me.nallen.modularCodeGeneration.description.haml.Importer
import java.io.File

class DescriptionTests : StringSpec() {
    init {
        File("examples").list().forEach {
            val folder = File("examples", it)
            if(folder.isDirectory) {
                val main = File(folder, "main.yaml")

                if(main.exists() && main.isFile) {
                    ("Can Import " + it) {
                        Importer.import(main.absolutePath)
                    }
                }
            }
        }
    }

}