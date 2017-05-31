package me.nallen.modularCodeGeneration

import me.nallen.modularCodeGeneration.hybridAutomata.*

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
    ha.addLocation(Location("q0"))
    ha.addLocation(Location("q1"))
    ha.addLocation(Location("q2"))
    ha.addLocation(Location("q3"))

    println(ha)
}