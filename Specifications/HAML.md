# Hybrid Automata Modelling Language (HAML)

## Introduction

HAML is a modelling language for describing hybrid systems that are compositions of a series of Hybrid Automata.
This document acts as the HAML specification, built on top of the [YAML 1.2 specification](http://yaml.org/spec/1.2/spec.html).
This specification allows for the describing of hybrid systems in a formal manner, which can then be used for aspects such as code generation, documentation, or any other purpose.

## Table of Contents

- Specification
    - Includes
- Schema
    - HAML Document Root
    - Definition

## Specification

Idk, talk about something

Uses YAML 1.2 as its base

### Includes

Adds support for `!include` statements

## Schema

Required vs Optional

### HAML Document Root

Intro to the Root

#### Fields

The following table lists all the possible fields for the HAML Document Root:

| Name | Type | Description |
|---|---|---|
| name | String | The name of this Hybrid Network. |
| definitions | Map[String, Definition] | A set of definitions of Hybrid Automata that can be instantiated. |
| instances | Map[String, Instance] | A set of instances of previously defined Hybrid Automata. |
| mappings? | Map[String, Formula] | A set of mappings that determine the value of each input of each Instance. |
| codegenConfig? | Codegen Configuration | A list of settings available for the default code generation logic in this tool. |


#### Example

```
example
```


### Definition

Introduction

#### Fields

| Name | Type | Description |
|---|---|---|
| inputs? | Map[String, Variable Definition] | The variables that this Hybrid Automata accepts as inputs. |
| outputs? | Map[String, Variable Definition] | The variables that this Hybrid Automata emits as outputs. |
| parameters? | Map[String, [Variable Definition](#variable-definition)] | The parameters that are available for configuration of this Hybrid Automata. |
| locations? | Map[String, Location] | The locations that exist inside this Hybrid Automata. |
| functions? | Map[String, Function] | A set of functions that exist inside this Hybrid Automata. |
| initialisation | Initialisation | Sets the initialisation options for the Hybrid Automata (location, variable states, etc.). |

#### Example

```
example
```


### Variable Definition

Introduction

#### Fields

| Name | Type | Description |
|---|---|---|
| type | Variable Type | The type of the variable. |
| default? | Formula | The default value for the variable. |
| delayableBy? | Formula | An amount of time that this variable could possibly be delayed by. Used in code generation. |

#### Example

```
example
```
