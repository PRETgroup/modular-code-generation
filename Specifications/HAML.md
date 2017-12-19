# Hybrid Automata Modelling Language (HAML)

#### Version: 0.1.0

## Introduction

HAML is a modelling language for describing hybrid systems that are compositions of a series of Hybrid Automata.
This document acts as the HAML specification, built on top of the [YAML 1.2 specification](http://yaml.org/spec/1.2/spec.html).
This specification allows for the describing of hybrid systems in a formal manner, which can then be used for aspects such as code generation, documentation, or any other purpose.

## Table of Contents

- [Definitions](#definitions)
    - [Hybrid Automata](#hybrid-automata)
    - [Hybrid Network](#hybrid-network)
    - [Ordinary Differential Equation](#ordinary-differerential-equation)
- [Specification](#specification)
    - [Includes](#includes)
- [Schema](#schema)
    - [HAML Document Root](#haml-document-root)
    - [Definition](#definition)
    - [Variable Definition](#variable-definition)
    - [Variable Type](#variable-type)
    - [Location](#location)
    - [Transition](#transition)
    - [Function](#function)
    - [Initialisation](#initialisation)
    - [Instance](#instance)
    - [Program](#program)
    - [Formula](#formula)
    - [Codegen Configuration](#codegen-configuration)
    - [Execution Settings](#execution-settings)
    - [Logging Settings](#logging-settings)
    - [Parametrisation Method](#parametrisation-method)

## Definitions

#### Hybrid Automata
A form of automata that supports both continuous and discrete parts.
Continuous aspects of the automata are specified through [ODEs](#ordinary-differential-equations), while discrete aspects are captured through locations and transitions between them.

#### Hybrid Network
A network of [Hybrid Automata](#hybrid-automata) that can be connected through their inputs and outputs in order to create a complete system.

#### Ordinary Differential Equation
Shortened to ODE, these are equations which describe how a continuous variable evolves over time by specifying its rate of change or gradient.

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

```yaml
name: heart

definitions:
  Cell: !include cell.yaml
  Path: !include path.yaml

instances:
  !include cells.yaml
  !include paths.yaml

mappings:
  !include mappings.yaml

codegenConfig:
  execution:
    stepSize: 0.00001
    simulationTime: 10
  logging:
    file: out.csv
    fields:
      - SA.v
      - RA.v
      - OS.v
      - Fast.v
      - AV.v
      - His.v
      - RBB.v
      - RVA.v
      - RV.v
  parametrisationMethod: COMPILE_TIME
  maximumInterTransitions: 1
  requireOneIntraTransitionPerTick: false
```


### Definition

Introduction

#### Fields

| Name | Type | Description |
|---|---|---|
| inputs? | Map[String, [Variable Type](#variable-type) \| [Variable Definition](#variable-definition)] | The variables that this Hybrid Automata accepts as inputs. |
| outputs? | Map[String, [Variable Type](#variable-type) \| [Variable Definition](#variable-definition)] | The variables that this Hybrid Automata emits as outputs. |
| parameters? | Map[String, [Variable Type](#variable-type) \| [Variable Definition](#variable-definition)] | The parameters that are available for configuration of this Hybrid Automata. |
| locations? | Map[String, [Location](#location)] | The locations that exist inside this Hybrid Automata. |
| functions? | Map[String, [Function](#function)] | A set of functions that exist inside this Hybrid Automata. |
| initialisation | [Initialisation](#initialisation) | Sets the initialisation options for the Hybrid Automata (location, variable states, etc.). |

#### Example

```yaml
inputs:
  g: REAL
outputs:
  v: REAL
  resting: BOOLEAN
  stimulated: BOOLEAN
locations:
  q0:
    invariant: v < V_T && g < V_T
    flow:
      v_x: C1 * v_x
      v_y: C2 * v_y
      v_z: C3 * v_z
    update:
      v: v_x - v_y + v_z
      resting: true
    transitions:
      - to: q1
        guard: g >= V_T
        update:
          v_x: 0.3 * v
          v_y: 0.0 * v
          v_z: 0.7 * v
          theta: v / 44.5
          f_theta: f(v / 44.5)
  q1:
    invariant: v < V_T && g > 0
    flow:
      v_x: C4 * v_x + C7 * g
      v_y: C5 * v_y + C8 * g
      v_z: C6 * v_z + C9 * g
    update:
      v: v_x - v_y + v_z
    transitions:
      - to: q2
        guard: v == V_T
        update:
          resting: false
      - to: q0
        guard: g <= 0 && v < V_T
  q2:
    invariant: v < V_O - 80.1 * sqrt(theta)
    flow:
      v_x: C10 * v_x
      v_y: C11 * v_y
      v_z: C12 * v_z
    update:
      v: v_x - v_y + v_z
      stimulated: true
    transitions:
      - to: q3
        guard: v == V_O - 80.1 * sqrt(theta)
        update:
          stimulated: false
  q3:
    invariant: v > V_R
    flow:
      v_x: C13 * v_x * f_theta
      v_y: C14 * v_y * f_theta
      v_z: C15 * v_z
    update:
      v: v_x - v_y + v_z
    transitions:
      - to: q0
        guard: v == V_R
functions:
  f:
    inputs:
      theta: REAL
    logic: |
      if(theta >= v_n_R_max) {
        return 4.03947
      }

      return 0.29*exp(62.89*theta) + 0.70*exp(-10.99*theta)
initialisation:
  state: q0
  valuations:
    resting: true
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

```yaml
type: REAL
default: -8.7
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

```yaml
invariant: v > V_R
flow:
  v_x: C13 * v_x * f_theta
  v_y: C14 * v_y * f_theta
  v_z: C15 * v_z
update:
  v: v_x - v_y + v_z
transitions:
  - to: q0
    guard: v == V_R
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

```yaml
to: q1
guard: g >= V_T
update:
  v_x: 0.3 * v
  v_y: 0.0 * v
  v_z: 0.7 * v
  theta: v / 44.5
  f_theta: f(v / 44.5)
```


### Function

Introduction

The return type (if any) is automatically generated from the logic

#### Fields

| Name | Type | Description |
|---|---|---|
| inputs? | Map[String, [Variable Type](#variable-type) \| [Variable Definition](#variable-definition)] | The set of inputs that this function accepts. |
| logic | [Program](#program) | The code that this function will perform when invoked. |

#### Example

```yaml
inputs:
  theta: REAL
logic: |
  if(theta >= v_n_R_max) {
    return 4.03947
  }

  return 0.29*exp(62.89*theta) + 0.70*exp(-10.99*theta)
```


### Initialisation

Introduction

#### Fields

| Name | Type | Description |
|---|---|---|
| state | String | The initial state that the Hybrid Automata will start in. |
| valuations? | Map[String, [Formula](#formula)] | The initial set of valuations for variables in the Hybrid Automata. |

#### Example

```yaml
state: q0
valuations:
  resting: true
```


### Instance

Introduction

#### Fields

| Name | Type | Description |
|---|---|---|
| type | String | The previously declared definition that this instance instantiates. |
| parameters? | Map[String, [Formula](#formula)] | The values of any parameters inside the previous declaration. Any parameters which do not have an entry here will inherit their default value (if any). |

#### Example

```yaml
type: Cell
parameters:
  C14: 20
  autorhythmic_rate: 100
```


### Program

Introduction

- Inherits String
- Each line is a [Formula](#formula)

#### Example

```yaml
example
```


### Formula

Introduction

- Inherits String
- Subset of math operations

#### Example

```yaml
example
```


### Codegen Configuration

Introduction

#### Fields

| Name | Type | Description |
|---|---|---|
| indentSize? | Int | The indentation size (in spaces) for the generated code. Use negative numbers for tabs. |
| execution? | [Execution Settings](#execution-settings) | The settings for the execution properties of the generated code. |
| logging? | [Logging Settings](#logging-settings) | The settings for the execution properties of the generated code. |
| parametrisationMethod? | [Parametrisation Method](#parametrisation-method) | The method to use for parametrisation when code is generated. |
| maximumInterTransitions? | Int | The maximum number of inter-location transitions that can be taken within each "step". In Hybrid Automata semantics these transitions should be instantaneous and this aims to replicate that to some degree. |
| requireOneIntraTransitionPerTick? | Boolean | Whether or not to require an intra-location transition (i.e. ODEs) within each "step". The evolution of ODEs is the only aspect of Hybrid Automata that should take any time.

#### Example

```yaml
indentSize: 4
execution:
  stepSize: 0.00001
  simulationTime: 10
logging:
  enable: false
parametrisationMethod: COMPILE_TIME
maximumInterTransitions: 1
requireOneIntraTransitionPerTick: false
```


### Execution Settings

Introduction

#### Fields

| Name | Type | Description |
|---|---|---|
| stepSize? | Double | The step size that is used for discretising the ODEs during execution. |
| simulationTime | Double | The time that will be simulated when the generated code is executed. |

#### Example

```yaml
stepSize: 0.00001
simulationTime: 10
```


### Logging Settings

Introduction

#### Fields

| Name | Type | Description |
|---|---|---|
| enable? | Boolean | Whether or not to enable logging of outputs. |
| interval? | Double | The interval at which to output log results to the file. For best results this should be an integer multiple of the step size. |
| file? | String | The file where the logging output should be placed. |
| fields? | String[] | The list of fields to output when logging. By default this will contain every output from every [Instance](#instance). |

#### Example

```yaml
enable: true
interval: 0.005
file: out.csv
fields:
  - SA.v
  - RA.v
  - OS.v
  - Fast.v
  - AV.v
  - His.v
  - RBB.v
  - RVA.v
  - RV.v
```


### Parametrisation Method

Introduction

This is an enum!

#### Enum Values

| Value | Description |
|---|---|
| `COMPILE_TIME` | Parameters will be set at the point of code generation. A file will be created for each [Instance](#instance) which results in a larger code size, but potentially faster execution. |
| `RUN_TIME` | Parameters will be set dynamically when the generated code is executed. Only one file will be created for each [Definition](#definition) which results in smaller code size, but likely slower execution. |
