package me.nallen.modularCodeGeneration.codeGen

data class Configuration(var indentSize: Int = 4) {
    fun getIndent(depth: Int = 1): String {
        var indent = "\t"
        if(indentSize > 0)
            indent = " ".repeat(indentSize)

        return indent.repeat(depth)
    }
}