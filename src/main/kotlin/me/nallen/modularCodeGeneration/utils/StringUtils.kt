package me.nallen.modularCodeGeneration.utils

fun String.isAllUpperCase(): Boolean {
    for(char in this) {
        if(!char.isUpperCase() && !char.isDigit())
            return false
    }
    return true
}

fun String.splitIntoWords(): List<String> {
    if (this.contains(" "))
    {
        return this.split(' ')
    }
    else if (this.contains("-"))
    {
        return this.split('-')
    }
    else if(this.contains("_"))
    {
        return this.split('_')
    }
    else
    {
        // Try camelCase or PascalCase
        val regex = Regex("^([A-Z][a-z0-9]*)+$")

        if(regex.matches(this.capitalize())) {
            val smallerRegex = Regex("[A-Z][a-z0-9]*")
            val matches = smallerRegex.findAll(this.capitalize())

            val words = ArrayList<String>()
            for(match in matches) {
                words.add(match.groupValues[0])
            }

            return words
        }
    }

    return listOf(this)
}

private fun List<String>.convertToCamelCase(): String {
    val builder = StringBuilder()

    var first = true
    for(word in this) {
        if(first)
            builder.append(word.toLowerCase())
        else
            builder.append(word.toLowerCase().capitalize())
        first = false
    }

    return builder.toString().trim()
}

fun String.convertWordDelimiterConvention(newConvention: NamingConvention): String {
    return arrayOf(this).convertWordDelimiterConvention(newConvention)
}

fun Array<out String>.convertWordDelimiterConvention(newConvention: NamingConvention): String {
    val words = ArrayList<String>()
    for(item in this) {
        if(item.isAllUpperCase())
            words.addAll(item.toLowerCase().splitIntoWords())
        else
            words.addAll(item.splitIntoWords())
    }

    return when(newConvention) {
        NamingConvention.SNAKE_CASE -> words.joinToString("_").toLowerCase()
        NamingConvention.UPPER_SNAKE_CASE -> words.joinToString("_").toUpperCase()
        NamingConvention.KEBAB_CASE -> words.joinToString("-").toLowerCase()
        NamingConvention.UPPER_KEBAB_CASE -> words.joinToString("-").toUpperCase()
        NamingConvention.CAMEL_CASE -> words.convertToCamelCase()
        NamingConvention.UPPER_CAMEL_CASE -> words.convertToCamelCase().capitalize()
    }
}

enum class NamingConvention {
    SNAKE_CASE, UPPER_SNAKE_CASE, KEBAB_CASE, UPPER_KEBAB_CASE, CAMEL_CASE, UPPER_CAMEL_CASE
}
