package me.nallen.modularCodeGeneration

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec
import me.nallen.modularCodeGeneration.parseTree.ParseTreeItem

data class EquationSet(
        val original: String,
        val parenthesised: String,
        val result: Double? = null
)

class ParseTreeTests : StringSpec() {
    init {
        val equations = listOf(
                EquationSet("1 + 2 - 3", "(1 + 2) - 3", 0.0),
                EquationSet("5 + 3 * 2", "5 + (3 * 2)", 11.0),
                EquationSet("5 / 3 * 2", "(5 / 3) * 2", 3.3333),
                EquationSet("1 / 0.5 / 4", "(1 / 0.5) / 4", 0.5),
                EquationSet("4 * pow(4, 6 + 2) - 7", "(4 * pow(4, (6 + 2))) - 7", 262137.0),
                EquationSet("6 + -7 * -4", "6 + ((-7) * (-4))", 34.0),
                EquationSet("9 + sqrt(7.234)", "9 + sqrt(7.234)", 22.6896),
                EquationSet("a == 7 || b != 5", "(a == 7) || (b != 5)"),
                EquationSet("a <= 3 && b >= 2 || c < 2 || d > 7", "(((a <= 3) && (b >= 2)) || (c < 2)) || (d > 7)"),
                EquationSet("!b == 5", "(!b) == 5"),
                EquationSet("!(b == 5)", "!(b == 5)")
        )

        for(equation in equations) {
            ("Precedence of " + equation.original) {
                ParseTreeItem.generate(equation.original) shouldBe ParseTreeItem.generate(equation.parenthesised)
            }

//            if(equation.result != null) {
//                "Result of " + equation.original {
//                    ParseTreeItem.generate(equation.original).evaluate() shouldBe equation.result
//                }
//            }
        }
    }
}