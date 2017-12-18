# Hybrid Automata Modelling Language (HAML)

## Introduction

HAML is a modelling language for describing hybrid systems that are compositions of a series of Hybrid Automata.
This document acts as the HAML specification, built on top of the [YAML 1.2 specification](http://yaml.org/spec/1.2/spec.html).
This specification allows for the describing of hybrid systems in a formal manner, which can then be used for aspects such as code generation, documentation, or any other purpose.

## Table of Contents

- [Specification](#specification)
    - [Includes](#includes)
- [Schema](#schema)
    - [HAML Document Root](#haml-document-root)
    - [Definition](#definition)
    - [Variable Definition](#variable-definition)

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
| definitions | Map[String, [Definition](#definition)] | A set of definitions of Hybrid Automata that can be instantiated. |
| instances | Map[String, [Instance](#instance)] | A set of instances of previously defined Hybrid Automata. |
| mappings? | Map[String, [Formula](#formula)] | A set of mappings that determine the value of each input of each Instance. |
| codegenConfig? | [Codegen Configuration](#codegen-configuration) | A list of settings available for the default code generation logic in this tool. |


#### Example

```
example
```


### Definition

Introduction

#### Fields

| Name | Type | Description |
|---|---|---|
| inputs? | Map[String, [Variable Definition](#variable-definition)] | The variables that this Hybrid Automata accepts as inputs. |
| outputs? | Map[String, [Variable Definition](#variable-definition)] | The variables that this Hybrid Automata emits as outputs. |
| parameters? | Map[String, [Variable Definition](#variable-definition)] | The parameters that are available for configuration of this Hybrid Automata. |
| locations? | Map[String, [Location](#location)] | The locations that exist inside this Hybrid Automata. |
| functions? | Map[String, [Function](#function)] | A set of functions that exist inside this Hybrid Automata. |
| initialisation | [Initialisation](#initialisation) | Sets the initialisation options for the Hybrid Automata (location, variable states, etc.). |

#### Example

```
example
```


### Variable Definition

Introduction

#### Fields

| Name | Type | Description |
|---|---|---|
| type | [Variable Type](#variable-type) | The type of the variable. |
| default? | [Formula](#formula) | The default value for the variable. |
| delayableBy? | [Formula](#formula) | An amount of time that this variable could possibly be delayed by. Used in code generation. |

#### Example

```
example
```


### Variable Type

Introduction

This is an enum!

#### Enum Values

| Value | Description |
|---|---|
| `BOOLEAN` | A boolean variable. |
| `REAL` | A real-numbered variable. |


### Location

Introduction

#### Fields

| Name | Type | Description |
|---|---|---|
| invariant? | [Formula](#formula) | The invariant that exists on this location. For control to remain in this location the invariant must hold true. |
| flow? | Map[String, [Formula](#formula)] | The set of flow constraints that exist for each ODE, these constraints will transform the values of the variables while control remains in this location. |
| update? | Map[String, [Formula](#formula)] | A set of discrete operations that are done while inside this location. |
| transitions? | [Transition](#transition)[] | A set of transitions that exist out of this location. |

#### Example

```
example
```


### Transition

Introduction

#### Fields

| Name | Type | Description |
|---|---|---|
| to | String | The destination location of this transition. |
| guard? | [Formula](#formula) | The guard that protects when this transition is "active" and can be taken. |
| update? | Map[String, [Formula](#formula)] | A set of discrete operations that are done when this transition is taken. |

#### Example

```
example
```


### Function

Introduction

The return type (if any) is automatically generated from the logic

#### Fields

| Name | Type | Description |
|---|---|---|
| inputs? | Map[String, [Variable Definition](#variable-definition)] | The set of inputs that this function accepts. |
| logic | [Program](#program) | The code that this function will perform when invoked. |

#### Example

```
example
```


### Initialisation

Introduction

#### Fields

| Name | Type | Description |
|---|---|---|
| state | String | The initial state that the Hybrid Automata will start in. |
| valuations? | Map[String, [Formula](#formula)] | The initial set of valuations for variables in the Hybrid Automata. |

#### Example

```
example
```


### Instance

Introduction

#### Fields

| Name | Type | Description |
|---|---|---|
| type | String | The previously declared definition that this instance instantiates. |
| parameters? | Map[String, [Formula](#formula)] | The values of any parameters inside the previous declaration. Any parameters which do not have an entry here will inherit their default value (if any). |

#### Example

```
example
```
