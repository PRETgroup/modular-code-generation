package me.nallen.modularCodeGeneration

import me.nallen.modularCodeGeneration.hybridAutomata.*
import me.nallen.modularCodeGeneration.parseTree.*

/**
 * Created by nall426 on 31/05/2017.
 */

fun getGreeting(): String {
    val words = mutableListOf<String>()
    words.add("Hello,")
    words.add("world!")

    return words.joinToString(separator = " ")
}

fun main(args: Array<String>) {
    var ha = HybridAutomata()
            .addLocation("q0")
            .addLocation("q1")
            .addLocation("q2")
            .addLocation("q3")
            .addEdge("q0","q1")
            .addEdge("q1","q0")
            .addEdge("q1","q2")
            .addEdge("q2","q3")
            .addEdge("q3","q0")

    //println(ha)

    var parsed = GenerateParseTreeFromString("!(5 < (x + 2)) && x2 > 7")

    println(parsed.generateString())
}