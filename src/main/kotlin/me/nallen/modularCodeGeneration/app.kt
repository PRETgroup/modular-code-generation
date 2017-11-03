package me.nallen.modularCodeGeneration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import me.nallen.modularCodeGeneration.codeGen.CodeGenLanguage
import me.nallen.modularCodeGeneration.codeGen.CodeGenManager
import me.nallen.modularCodeGeneration.finiteStateMachine.FiniteNetwork
import me.nallen.modularCodeGeneration.hybridAutomata.*
import me.nallen.modularCodeGeneration.parseTree.*

fun main(args: Array<String>) {
    val network = HybridNetwork();

    network.addDefinition(
            HybridAutomata("Cell")
            .addContinuousVariable("g", Locality.EXTERNAL_INPUT)
            .addContinuousVariable("v", Locality.EXTERNAL_OUTPUT)
            .addParameter("C1").addParameter("C2").addParameter("C3")
            .addParameter("C4").addParameter("C5").addParameter("C6")
            .addParameter("C7").addParameter("C8").addParameter("C9")
            .addParameter("C10").addParameter("C11").addParameter("C12")
            .addParameter("C13").addParameter("C14").addParameter("C15")
            .addParameter("V_O").addParameter("V_T").addParameter("V_R")
            .addLocation(
                    "q0",
                    ParseTreeItem.generate("v < V_T && g < V_T"),
                    linkedMapOf(
                            "v_x" to ParseTreeItem.generate("C1 * v_x"),
                            "v_y" to ParseTreeItem.generate("C2 * v_y"),
                            "v_z" to ParseTreeItem.generate("C3 * v_z")
                    ),
                    linkedMapOf(
                            "v" to ParseTreeItem.generate("v_x - v_y + v_z")
                    )
            )
            .addLocation(
                    "q1",
                    ParseTreeItem.generate("v < V_T && g > 0"),
                    linkedMapOf(
                            "v_x" to ParseTreeItem.generate("C4 * v_x + C7 * g"),
                            "v_y" to ParseTreeItem.generate("C5 * v_y + C8 * g"),
                            "v_z" to ParseTreeItem.generate("C6 * v_z + C9 * g")
                    ),
                    linkedMapOf(
                            "v" to ParseTreeItem.generate("v_x - v_y + v_z")
                    )
            )
            .addLocation(
                    "q2",
                    ParseTreeItem.generate("v < V_O - 80.1 * sqrt(theta)"),
                    linkedMapOf(
                            "v_x" to ParseTreeItem.generate("C10 * v_x"),
                            "v_y" to ParseTreeItem.generate("C11 * v_y"),
                            "v_z" to ParseTreeItem.generate("C12 * v_z")
                    ),
                    linkedMapOf(
                            "v" to ParseTreeItem.generate("v_x - v_y + v_z")
                    )
            )
            .addLocation(
                    "q3",
                    ParseTreeItem.generate("v > V_R"),
                    linkedMapOf(
                            "v_x" to ParseTreeItem.generate("C13 * v_x * f_theta"),
                            "v_y" to ParseTreeItem.generate("C14 * v_y * f_theta"),
                            "v_z" to ParseTreeItem.generate("C15 * v_z")
                    ),
                    linkedMapOf(
                            "v" to ParseTreeItem.generate("v_x - v_y + v_z")
                    )
            )
            .addEdge(
                    "q0", "q1",
                    ParseTreeItem.generate("g >= V_T"),
                    update = linkedMapOf(
                            "v_x" to ParseTreeItem.generate("0.3 * v"),
                            "v_y" to ParseTreeItem.generate("0.0 * v"),
                            "v_z" to ParseTreeItem.generate("0.7 * v"),
                            "theta" to ParseTreeItem.generate("v / 44.5"),
                            "f_theta" to ParseTreeItem.generate("theta")
                    )
            )
            .addEdge("q1", "q0", ParseTreeItem.generate("g <= 0 && v < V_T"))
            .addEdge("q1", "q2", ParseTreeItem.generate("v > V_T"))
            .addEdge("q2", "q3", ParseTreeItem.generate("v >= V_O - 80.1 * sqrt(theta)"))
            .addEdge("q3", "q0", ParseTreeItem.generate("v <= V_R"))
            .setInit(Initialisation(
                    "q0",
                    linkedMapOf(
                            "v_x" to ParseTreeItem.generate("0"),
                            "v_y" to ParseTreeItem.generate("0"),
                            "v_z" to ParseTreeItem.generate("0"),
                            "theta" to ParseTreeItem.generate("0"),
                            "f_theta" to ParseTreeItem.generate("0")
                    )
            ))
    )

    network.addInstance(
            "SA",
            AutomataInstance(
                    "Cell",
                    linkedMapOf(
                            "C1" to ParseTreeItem.generate("-8.7"),
                            "C2" to ParseTreeItem.generate("-23.6"),
                            "C3" to ParseTreeItem.generate("-6.9"),
                            "C4" to ParseTreeItem.generate("-33.2"),
                            "C5" to ParseTreeItem.generate("-190.9"),
                            "C6" to ParseTreeItem.generate("-45.5"),
                            "C7" to ParseTreeItem.generate("777200"),
                            "C8" to ParseTreeItem.generate("58900"),
                            "C9" to ParseTreeItem.generate("276600"),
                            "C10" to ParseTreeItem.generate("75.9"),
                            "C11" to ParseTreeItem.generate("20"),
                            "C12" to ParseTreeItem.generate("-190.4"),
                            "C13" to ParseTreeItem.generate("-12.9"),
                            "C14" to ParseTreeItem.generate("6826.5"),
                            "C15" to ParseTreeItem.generate("2"),
                            "V_O" to ParseTreeItem.generate("131.1"),
                            "V_T" to ParseTreeItem.generate("44.5"),
                            "V_R" to ParseTreeItem.generate("30")
                    )
            )
    )

    network.addInstance(
            "RV",
            AutomataInstance(
                    "Cell",
                    linkedMapOf(
                            "C1" to ParseTreeItem.generate("-8.7"),
                            "C2" to ParseTreeItem.generate("-23.6"),
                            "C3" to ParseTreeItem.generate("-6.9"),
                            "C4" to ParseTreeItem.generate("-33.2"),
                            "C5" to ParseTreeItem.generate("-190.9"),
                            "C6" to ParseTreeItem.generate("-45.5"),
                            "C7" to ParseTreeItem.generate("777200"),
                            "C8" to ParseTreeItem.generate("58900"),
                            "C9" to ParseTreeItem.generate("276600"),
                            "C10" to ParseTreeItem.generate("75.9"),
                            "C11" to ParseTreeItem.generate("11"),
                            "C12" to ParseTreeItem.generate("-190.4"),
                            "C13" to ParseTreeItem.generate("-12.9"),
                            "C14" to ParseTreeItem.generate("6826.5"),
                            "C15" to ParseTreeItem.generate("2"),
                            "V_O" to ParseTreeItem.generate("131.1"),
                            "V_T" to ParseTreeItem.generate("44.5"),
                            "V_R" to ParseTreeItem.generate("30")
                    )
            )
    )

    network.addMapping(
            AutomataVariablePair("SA", "g"),
            AutomataVariablePair("RV", "v")
    )

    network.addMapping(
            AutomataVariablePair("RV", "g"),
            AutomataVariablePair("SA", "v")
    )

    val fsmNetwork = FiniteNetwork.generateFromHybridNetwork(network)

    CodeGenManager.generateForNetwork(fsmNetwork, CodeGenLanguage.C, "Generated")
}