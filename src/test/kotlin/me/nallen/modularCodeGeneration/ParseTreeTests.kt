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
                EquationSet("6 == 7 || 9 != 5", "(6 == 7) || (9 != 5)", true),
                EquationSet("8 != 6 + true", "8 != (6 + true)", true),
                EquationSet("(2.5 <= 3 && 2.5 >= 2 || 4 < 2) && 5 - false", "(((2.5 <= 3) && (2.5 >= 2)) || (4 < 2)) && (5 - false)", true),
                EquationSet("!1 == 5", "(!1) == 5", false),
                EquationSet("!(1 == 5)", "!(1 == 5)", true),
                EquationSet("false || 5 < 7", "(false) || (5 < 7)", true),
                EquationSet("true && 5 > 7", "(true) && (5 > 7)", false),
                EquationSet("(5 > 7) || (5 - 5)", "(5 > 7) || (5 - 5)", false),
                EquationSet("(5 < 7) + my_custom_function(a, b, test() )", "(5 < 7) + my_custom_function(a, b, test() )"),
                EquationSet("2 + b", "2 + b"),
                EquationSet("a || false", "a || false"),
                EquationSet("true && test()", "true && test()"),

                EquationSet("my_custom_function(a, b c)"),
                EquationSet("my_custom_function(a, b, c"),
                EquationSet("my_custom_function(a, b, c -)"),
                EquationSet("my_custom_function(a, , c)"),
                EquationSet("broken_function_52<3>"),
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
            else if(equation.result == null) {
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
            else if(equation.parenthesised != null) {
                "Should not evaluate ${equation.original}" {
                    shouldThrow<IllegalArgumentException> {
                        ParseTreeItem.generate(equation.original).evaluate()
                    }
                }
            }
        }
    }

    fun matchDouble(testValue: Double, tolerance: Double = 0.001) = object : Matcher<Double> {
        override fun test(value: Double) = Result(value + tolerance > testValue && value - tolerance < testValue, "expected: $testValue but was: $value (using tolerance $tolerance)")
    }

}