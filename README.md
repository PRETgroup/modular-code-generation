# Piha

Aka. Modular Code Generation

## Table of Contents

- [What is this?](#what-is-this)
- [How to use](#how-to-use)
    - [From Source](#from-source)
    - [Pre-generated File](#pre-generated-file)
        - [Example Commands](#example-commands)
- [Examples](#examples)
- [Publications](#publications)

## What is this?

This is a tool to automatically generate code for the execution of networks of Hybrid Automata.
Networks are described using the [HAML Spec](specs/HAML.md) (also part of this project).

This tool is developed by the [PRETzel Research Group](http://pretzel.ece.auckland.ac.nz/) at The University of Auckland in New Zealand.
As such, it is currently to be treated more as a research project, and bugs may be present or features may be missing.


## How to use

There are two ways to use the code generation: either by compiling from source, or downloading one of the pre-generated JAR files.
The procedure for each of these is outlined below.

### From Source

This tool is written in [Kotlin](https://kotlinlang.org/), and uses [Gradle](https://gradle.org/) for its build system.

To run the debug program (`app.kt`) simply run `gradle run` (or `gradlew run` to use the bundled Gradle version).

To build a new executable JAR file for distribution, run `gradle build` (or `gradlew build`).
This will create a "fat" JAR file (includes all Kotlin runtime dependencies) whose main file on execution is defined in `Cli.kt`.

### Pre-generated File

Pre-generated JAR files are available for you to use if you don't want to compile from source.
Simply download the latest release (`piha.jar`) from the [Releases](https://github.com/nallen01/modular-code-generation/releases) page on Github.
This executable JAR file can then be run with the following arguments:

| Flag | Description |
|---|---|
| pos. 0 | **Required.** Path to the HAML Document for which you want to generate code for. |
| `-l`<br/>`--language` | The language to generate code for. Valid options are: `C`.<br /><br/>**Default:** `C` |
| `-o`<br/>`--output` | The directory to store the generated code in.<br/><br/>**Default:** `output` |

#### Example Commands

Generate **C code** for the file **MY_SPEC.yaml**:

`java -jar piha.jar MY_SPEC.yaml --language C`

Generate **C code** for the file **MY_SPEC.yaml** and put it in the folder **my_folder**:

`java -jar piha.jar MY_SPEC.yaml --language C -o my_folder`


## Examples

Several examples are maintained under the `examples` directory.

Each of these examples model some form of physical process.
A README exists in this `examples` directory that gives a brief explanation of each.


## Publications

The initial design of this tool was described in a *Design, Automation and Test in Europe (DATE) 2016* paper titled *Modular Code Generation for Emulating the Electrical Conduction System of the Human Heart*.
