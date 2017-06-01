package me.nallen.modularCodeGeneration

import me.nallen.modularCodeGeneration.hybridAutomata.*
import me.nallen.modularCodeGeneration.parseTree.*

/**
 * Created by nall426 on 31/05/2017.
 */

fun main(args: Array<String>) {
    val ha = HybridAutomata("Cell")
            .addLocation("q0", ParseTreeItem.generate("v < 44.5 && g < 44.5"))
            .addLocation("q1", ParseTreeItem.generate("v < 44.5 && g > 0"))
            .addLocation("q2", ParseTreeItem.generate("v < 131.1 - 80.1 * sqrt(theta)"))
            .addLocation("q3", ParseTreeItem.generate("v > 30"))
            .addEdge("q0","q1", ParseTreeItem.generate("g >= 44.5"))
            .addEdge("q1","q0", ParseTreeItem.generate("g <= 0 && v < 44.5"))
            .addEdge("q1","q2", ParseTreeItem.generate("v > 44.5"))
            .addEdge("q2","q3", ParseTreeItem.generate("v >= 131.1 - 80.1 * sqrt(theta)"))
            .addEdge("q3","q0", ParseTreeItem.generate("v <= 30"))

    println(ha)
}