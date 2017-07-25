package me.nallen.modularCodeGeneration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import me.nallen.modularCodeGeneration.codeGen.CodeGenLanguage
import me.nallen.modularCodeGeneration.codeGen.CodeGenManager
import me.nallen.modularCodeGeneration.codeGen.c.CCodeGenResult
import me.nallen.modularCodeGeneration.finiteStateMachine.FiniteStateMachine
import me.nallen.modularCodeGeneration.hybridAutomata.*
import me.nallen.modularCodeGeneration.parseTree.*

fun main(args: Array<String>) {
    val mapper: ObjectMapper = jacksonObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, false)

    //TODO: Declare parameters
    //TODO: Parameter synthesis option (compile time vs runtime)
    val ha = HybridAutomata("Cell")
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
                    hashMapOf(
                            "v_x" to ParseTreeItem.generate("C1 * v_x"),
                            "v_y" to ParseTreeItem.generate("C2 * v_y"),
                            "v_z" to ParseTreeItem.generate("C3 * v_z")
                    ),
                    hashMapOf(
                            "v" to ParseTreeItem.generate("v_x - v_y + v_z")
                    )
            )
            .addLocation(
                    "q1",
                    ParseTreeItem.generate("v < V_T && g > 0"),
                    hashMapOf(
                            "v_x" to ParseTreeItem.generate("C4 * v_x + C7 * g"),
                            "v_y" to ParseTreeItem.generate("C5 * v_y + C8 * g"),
                            "v_z" to ParseTreeItem.generate("C6 * v_z + C9 * g")
                    ),
                    hashMapOf(
                            "v" to ParseTreeItem.generate("v_x - v_y + v_z")
                    )
            )
            .addLocation(
                    "q2",
                    ParseTreeItem.generate("v < V_O - 80.1 * sqrt(theta)"),
                    hashMapOf(
                            "v_x" to ParseTreeItem.generate("C10 * v_x"),
                            "v_y" to ParseTreeItem.generate("C11 * v_y"),
                            "v_z" to ParseTreeItem.generate("C12 * v_z")
                    ),
                    hashMapOf(
                            "v" to ParseTreeItem.generate("v_x - v_y + v_z")
                    )
            )
            .addLocation(
                    "q3",
                    ParseTreeItem.generate("v > V_R"),
                    hashMapOf(
                            "v_x" to ParseTreeItem.generate("C13 * v_x * f_theta"),
                            "v_y" to ParseTreeItem.generate("C14 * v_y * f_theta"),
                            "v_z" to ParseTreeItem.generate("C15 * v_z")
                    ),
                    hashMapOf(
                            "v" to ParseTreeItem.generate("v_x - v_y + v_z")
                    )
            )
            .addEdge(
                    "q0", "q1",
                    ParseTreeItem.generate("g >= V_T"),
                    update = hashMapOf(
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
                    hashMapOf(
                            "v_x" to ParseTreeItem.generate("0"),
                            "v_y" to ParseTreeItem.generate("0"),
                            "v_z" to ParseTreeItem.generate("0"),
                            "theta" to ParseTreeItem.generate("0"),
                            "f_theta" to ParseTreeItem.generate("0")
                    )
            ))

    println(mapper.writeValueAsString(ha))

    val fsm = FiniteStateMachine.generateFromHybridAutomata(ha)
    println(mapper.writeValueAsString(fsm))

    val codegen = CodeGenManager.generateStringsForFSM(fsm, CodeGenLanguage.C)

    if (codegen is CCodeGenResult) {
        println("H File:")
        println(codegen.h)
        println()
        println("C File:")
        println(codegen.c)
    }
    else {
        println(mapper.writeValueAsString(codegen))
    }
}