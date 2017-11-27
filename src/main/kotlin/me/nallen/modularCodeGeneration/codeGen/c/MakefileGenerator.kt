package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.ParametrisationMethod
import me.nallen.modularCodeGeneration.hybridAutomata.AutomataInstance

object MakefileGenerator {
    private var instances: Map<String, AutomataInstance> = LinkedHashMap<String, AutomataInstance>()
    private var config: Configuration = Configuration()

    fun generate(name: String, instances: Map<String, AutomataInstance>, config: Configuration = Configuration()): String {
        this.instances = instances
        this.config = config

        val result = StringBuilder()

        result.appendln("TARGET = $name")
        result.appendln("CC = gcc")
        result.appendln("CFLAGS = -c -O2 -lm -Wall")
        result.appendln("LDFLAGS = -g -Wall -lm")
        result.appendln()

        result.appendln("build: $(TARGET)")
        result.appendln()

        val sources = ArrayList<String>()

        if(instances.isNotEmpty()) {
            if(config.parametrisationMethod == ParametrisationMethod.COMPILE_TIME) {
                for((name, instance) in instances) {
                    val deliminatedName = Utils.createFileName(name)
                    val deliminatedFolder = Utils.createFolderName(instance.automata)
                    result.append(generateCompileCommand(deliminatedName, listOf("$deliminatedFolder/$deliminatedName.c"), listOf("$deliminatedFolder/$deliminatedName.h", CCodeGenerator.CONFIG_FILE)))
                    result.appendln()
                    sources.add("Objects/$deliminatedName")
                }
            }
            else {
                val generated = ArrayList<String>()
                for((_, instance) in instances) {
                    if (!generated.contains(instance.automata)) {
                        generated.add(instance.automata)

                        val deliminatedName = Utils.createFileName(instance.automata)
                        result.append(generateCompileCommand(deliminatedName, listOf("$deliminatedName.c"), listOf("$deliminatedName.h", CCodeGenerator.CONFIG_FILE)))
                        result.appendln()
                        sources.add("Objects/$deliminatedName")
                    }
                }
            }
        }

        result.append(generateCompileCommand("runnable", listOf("runnable.c"), listOf(CCodeGenerator.CONFIG_FILE)))
        result.appendln()
        sources.add("Objects/runnable")

        result.append(generateLinkCommand("$(TARGET)", sources))
        result.appendln()

        result.appendln(".PHONY: clean")
        result.appendln("clean:")
        result.appendln("\t@echo Removing compiled binaries...")
        result.appendln("\t@rm -rf $(TARGET) Objects/* *~")
        result.appendln()

        return result.toString().trim()
    }

    private fun generateCompileCommand(name: String, sources: List<String>, dependencies: List<String>): String {
        val result = StringBuilder()

        result.append("Objects/$name:")
        for(source in sources) {
            result.append(" $source")
        }
        for(dependency in dependencies) {
            result.append(" $dependency")
        }
        result.appendln()
        result.appendln("\t@echo Building $name...")
        result.append("\t@mkdir -p Objects; $(CC) $(CFLAGS)")
        for(source in sources) {
            result.append(" $source")
        }
        result.appendln(" -o $@")

        return result.toString()
    }

    private fun generateLinkCommand(output: String, sources: List<String>): String {
        val result = StringBuilder()

        result.append("$output:")
        for(source in sources) {
            result.append(" $source")
        }
        result.appendln()
        result.appendln("\t@echo Building $output...")
        result.append("\t$(CC) $(LDFLAGS)")
        for(source in sources) {
            result.append(" $source")
        }
        result.appendln(" -o $@")

        return result.toString()
    }
}