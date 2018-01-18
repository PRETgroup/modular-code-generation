package me.nallen.modularCodeGeneration

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec
import me.nallen.modularCodeGeneration.utils.convertWordDelimiterConvention
import me.nallen.modularCodeGeneration.utils.NamingConvention

data class StringSet(
        val original: String,
        val outputs: Map<NamingConvention, String>
)

class StringUtilsTests : StringSpec() {
    init {
        val sets = listOf(
                StringSet("this is a test string", mapOf(
                        NamingConvention.SNAKE_CASE to "this_is_a_test_string",
                        NamingConvention.UPPER_SNAKE_CASE to "THIS_IS_A_TEST_STRING",
                        NamingConvention.KEBAB_CASE to "this-is-a-test-string",
                        NamingConvention.UPPER_KEBAB_CASE to "THIS-IS-A-TEST-STRING",
                        NamingConvention.CAMEL_CASE to "thisIsATestString",
                        NamingConvention.UPPER_CAMEL_CASE to "ThisIsATestString")
                ),
                StringSet("ThisIsATestString", mapOf(
                        NamingConvention.SNAKE_CASE to "this_is_a_test_string",
                        NamingConvention.UPPER_SNAKE_CASE to "THIS_IS_A_TEST_STRING",
                        NamingConvention.KEBAB_CASE to "this-is-a-test-string",
                        NamingConvention.UPPER_KEBAB_CASE to "THIS-IS-A-TEST-STRING",
                        NamingConvention.CAMEL_CASE to "thisIsATestString",
                        NamingConvention.UPPER_CAMEL_CASE to "ThisIsATestString")
                ),
                StringSet("THIS_IS_A_TEST_STRING", mapOf(
                        NamingConvention.SNAKE_CASE to "this_is_a_test_string",
                        NamingConvention.UPPER_SNAKE_CASE to "THIS_IS_A_TEST_STRING",
                        NamingConvention.KEBAB_CASE to "this-is-a-test-string",
                        NamingConvention.UPPER_KEBAB_CASE to "THIS-IS-A-TEST-STRING",
                        NamingConvention.CAMEL_CASE to "thisIsATestString",
                        NamingConvention.UPPER_CAMEL_CASE to "ThisIsATestString")
                ),
                StringSet("this-is-a-test-string", mapOf(
                        NamingConvention.SNAKE_CASE to "this_is_a_test_string",
                        NamingConvention.UPPER_SNAKE_CASE to "THIS_IS_A_TEST_STRING",
                        NamingConvention.KEBAB_CASE to "this-is-a-test-string",
                        NamingConvention.UPPER_KEBAB_CASE to "THIS-IS-A-TEST-STRING",
                        NamingConvention.CAMEL_CASE to "thisIsATestString",
                        NamingConvention.UPPER_CAMEL_CASE to "ThisIsATestString")
                ),
                StringSet("1test", mapOf(
                        NamingConvention.CAMEL_CASE to "1test")
                )
        )

        for(set in sets) {
            for(output in set.outputs) {
                "Convert '${set.original}' to ${output.key}" {
                    set.original.convertWordDelimiterConvention(output.key) shouldBe output.value
                }
            }
        }
    }

}