package me.nallen.modularcodegeneration.utils

/**
 * A set of utilities that operate on Strings (or Arrays of Strings).
 */

/**
 * Checks whether the given string is entirely upper case characters
 */
fun String.isAllUpperCase(): Boolean {
    for(char in this) {
        if(!char.isUpperCase() && !char.isDigit())
            return false
    }
    return true
}

/**
 * Splits the string up into its constituent words.
 * This could be in various different formats, in the following precedence:
 *     1. Space delimited
 *     2. kebab-case
 *     3. snake_case
 *     4. camelCase (or PascalCase)
 */
fun String.splitIntoWords(): List<String> {
    if (this.contains(" "))
    {
        // Space delimited
        return this.split(' ')
    }
    else if (this.contains("-"))
    {
        // kebab-case
        return this.split('-')
    }
    else if(this.contains("_"))
    {
        // snake_case
        return this.split('_')
    }
    else
    {
        // Try camelCase or PascalCase
        val regex = Regex("^([A-Z][a-z0-9]*)+$")

        // We capitalise the first letter in order to convert to PascalCase
        if(regex.matches(this.capitalize())) {
            // Get each individual word
            val smallerRegex = Regex("[A-Z][a-z0-9]*")
            val matches = smallerRegex.findAll(this.capitalize())

            val words = ArrayList<String>()
            for(match in matches) {
                words.add(match.groupValues[0])
            }

            return words
        }
    }

    // If we can't find any method we know about, assume it's just one word
    return listOf(this)
}

/**
 * Generates a string in camelCase format (lower case first letter) from a List of Strings
 */
private fun List<String>.convertToCamelCase(): String {
    val builder = StringBuilder()

    // The first word is special as it needs to have a lower case first letter
    var first = true
    for(word in this) {
        // The word we append also needs to have every non-first letter lower case
        if(first)
            builder.append(word.toLowerCase())
        else
            builder.append(word.toLowerCase().capitalize())
        first = false
    }

    return builder.toString().trim()
}

/**
 * Converts the delimiter method from the current to a provided one
 */
fun String.convertWordDelimiterConvention(newConvention: NamingConvention): String {
    return arrayOf(this).convertWordDelimiterConvention(newConvention)
    // Call the other extension method we have to do the actual conversion
}

/**
 * Converts the delimiter method from the current to a provided one
 */
fun Array<out String>.convertWordDelimiterConvention(newConvention: NamingConvention): String {
    // The list of strings that we have are sequences of words that get concatenated together.
    val words = ArrayList<String>()
    for(item in this) {
        // Each string needs to be split up into its constituent words
        if(item.isAllUpperCase())
            words.addAll(item.toLowerCase().splitIntoWords())
        else
            words.addAll(item.splitIntoWords())
    }

    // Return the string in its correct new naming convention
    return when(newConvention) {
        NamingConvention.SNAKE_CASE -> words.filter {it.isNotEmpty() }.joinToString("_").toLowerCase()
        NamingConvention.UPPER_SNAKE_CASE -> words.filter {it.isNotEmpty() }.joinToString("_").toUpperCase()
        NamingConvention.KEBAB_CASE -> words.filter {it.isNotEmpty() }.joinToString("-").toLowerCase()
        NamingConvention.UPPER_KEBAB_CASE -> words.filter {it.isNotEmpty() }.joinToString("-").toUpperCase()
        NamingConvention.CAMEL_CASE -> words.filter {it.isNotEmpty() }.convertToCamelCase()
        NamingConvention.UPPER_CAMEL_CASE -> words.filter {it.isNotEmpty() }.convertToCamelCase().capitalize()
    }
}

/**
 * The different output naming conventions that are supported and can be converted into
 */
enum class NamingConvention {
    SNAKE_CASE, UPPER_SNAKE_CASE, KEBAB_CASE, UPPER_KEBAB_CASE, CAMEL_CASE, UPPER_CAMEL_CASE
}
