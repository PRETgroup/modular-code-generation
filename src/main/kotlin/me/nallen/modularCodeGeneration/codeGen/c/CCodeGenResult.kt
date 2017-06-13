package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.CodeGenResult

data class CCodeGenResult(var c: String, var h: String): CodeGenResult()