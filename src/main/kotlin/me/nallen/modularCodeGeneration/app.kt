package me.nallen.modularCodeGeneration

import me.nallen.modularCodeGeneration.codeGen.CodeGenLanguage
import me.nallen.modularCodeGeneration.codeGen.CodeGenManager
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.ParametrisationMethod
import me.nallen.modularCodeGeneration.description.Importer
import me.nallen.modularCodeGeneration.hybridAutomata.HybridItem
import me.nallen.modularCodeGeneration.hybridAutomata.HybridNetwork
import kotlin.system.measureTimeMillis

/**
 * The main program that gets run when you call 'gradle run'.
 */
fun main(args: Array<String>) {
    var item: HybridItem = HybridNetwork()
    var config = Configuration()
    var time: Long

    // Times are recorded and output for debugging purposes

    // Import from the description
    time = measureTimeMillis {
        val imported = Importer.import("examples/dac/cardiac_grid/2.yaml")
        item = imported.first
        config = imported.second
    }
    println("Import time: $time ms")

    config = config.copy(parametrisationMethod = ParametrisationMethod.RUN_TIME)

    // Generate C code
    time = measureTimeMillis {
        CodeGenManager.generate(item, CodeGenLanguage.VHDL, "Generated", config)
    }
    println("Code Generation time: $time ms")
}

/* Changes to make

[x] change state reads to `STATE_TYPE'val(state_in)`
[x] change outputs to output properly
[x] remove internal signals
[x] change reads to input signals
[x] parameters in functions
[x] outputs need to have associated inputs too
[x] instance signals
[-] setting start / finish in automata
[x] setting start in network
[x] initial values for storage
[x] assign variables at start of tick

[x] done signals

[x] top level system

 */