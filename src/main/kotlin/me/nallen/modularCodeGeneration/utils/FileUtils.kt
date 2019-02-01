package me.nallen.modularCodeGeneration.utils

import java.io.File

/**
 * Generates a string that holds the relative path to the given File from the current directory
 */
fun File.getRelativePath(): String {
    // Let's get the desired path
    val currentPath = File("").absolutePath

    // If it is relative from here
    return if(this.absolutePath.startsWith(currentPath))
        // Then return the relative part
        this.absolutePath.substring(currentPath.length+1)
    else
        // Otherwise just return the absolute path
        this.absolutePath
}