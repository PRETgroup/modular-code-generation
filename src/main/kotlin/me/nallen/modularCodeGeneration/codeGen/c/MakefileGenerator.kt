package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.ParametrisationMethod
import me.nallen.modularCodeGeneration.finiteStateMachine.FiniteInstance
import me.nallen.modularCodeGeneration.finiteStateMachine.MachineVariablePair

object MakefileGenerator {
    private var instances: Map<String, FiniteInstance> = LinkedHashMap<String, FiniteInstance>()
    private var config: Configuration = Configuration()

    fun generate(instances: Map<String, FiniteInstance>, config: Configuration = Configuration()): String {
        this.instances = instances
        this.config = config

        val result = StringBuilder()

        val name = "NAME HERE"

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
                    result.append(generateCompileCommand(name, listOf("${instance.machine}/$name.c"), listOf("${instance.machine}/$name.h", "step.h")))
                    result.appendln()
                    sources.add("Objects/$name")
                }
            }
            else {
                val generated = ArrayList<String>()
                for((_, instance) in instances) {
                    if (!generated.contains(instance.machine)) {
                        generated.add(instance.machine)

                        result.append(generateCompileCommand(instance.machine, listOf("${instance.machine}.c"), listOf("${instance.machine}.h", "step.h")))
                        result.appendln()
                        sources.add("Objects/${instance.machine}")
                    }
                }
            }
        }

        result.append(generateCompileCommand("runnable", listOf("runnable.c"), listOf("step.h")))
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