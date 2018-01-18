package me.nallen.modularCodeGeneration

import io.kotlintest.matchers.*
import io.kotlintest.specs.StringSpec
import me.nallen.modularCodeGeneration.parseTree.ParseTreeItem
import me.nallen.modularCodeGeneration.parseTree.evaluate

data class EquationSet(
        val original: String,
        val parenthesised: String? = null,
        val result: Any? = null
)

class ParseTreeTests : StringSpec() {
    init {
        val equations = listOf(
                EquationSet("1 + 2 - 3", "(1 + 2) - 3", 0.0),
                EquationSet("5 + 3 * 2", "5 + (3 * 2)", 11.0),
                EquationSet("5 / 3 * 2", "(5 / 3) * 2", 3.3333),
                EquationSet("1 / 0.5 / 4", "(1 / 0.5) / 4", 0.5),
                EquationSet("4 * exp(6 + 2) - 7", "(4 * exp(6 + 2)) - 7", 11916.8319),
                EquationSet("6 + -7 * -4", "6 + ((-7) * (-4))", 34.0),
                EquationSet("9 + sqrt(7.234)", "9 + sqrt(7.234)", 11.6896),
                EquationSet("a == 7 || b != 5", "(a == 7) || (b != 5)"),
                EquationSet("a <= 3 && b >= 2 || c < 2 || d > 7", "(((a <= 3) && (b >= 2)) || (c < 2)) || (d > 7)"),
                EquationSet("!b == 5", "(!b) == 5"),
                EquationSet("!(b == 5)", "!(b == 5)"),
                EquationSet("2 + my_custom_function(a, b, test() )", "2 + my_custom_function(a, b, test() )"),

                EquationSet("my_custom_function(a, b c)"),
                EquationSet("my_custom_function(a, b, c"),
                EquationSet("my_custom_function(a, b, c -)"),
                EquationSet("5 * / 2"),
                EquationSet("4 3 - + 2"),
                EquationSet("5 * 2 / 3)")
        )

        for(equation in equations) {
            if(equation.parenthesised != null) {
                ("Precedence of " + equation.original) {
                    ParseTreeItem.generate(equation.original) shouldBe ParseTreeItem.generate(equation.parenthesised)
                }
            }
            else {
                ("Failure of " + equation.original) {
                    shouldThrow<IllegalArgumentException> {
                        ParseTreeItem.generate(equation.original)
                    }
                }
            }

            if(equation.result != null) {
                ("Result of " + equation.original) {
                    val result = ParseTreeItem.generate(equation.original).evaluate()

                    if(result is Double && equation.result is Double) {
                        result should matchDouble(equation.result)
                    }
                    else {
                        result shouldBe equation.result
                    }
                }
            }
        }
    }

    fun matchDouble(testValue: Double, tolerance: Double = 0.001) = object : Matcher<Double> {
        override fun test(value: Double) = Result(value + tolerance > testValue && value - tolerance < testValue, "expected: $testValue but was: $value (using tolerance $tolerance)")
    }

}