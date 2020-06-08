# Hybrid Automata Modelling Language (HAML)

#### Version: 0.1.4

## Introduction

HAML is a modelling language for describing hybrid systems that are compositions of a series of Hybrid Automata.
This document acts as the HAML specification, built on top of the [YAML 1.2 specification](http://yaml.org/spec/1.2/spec.html).
This specification allows for the describing of hybrid systems in a formal manner, which can then be used for aspects such as code generation, documentation, or any other purpose.


## Table of Contents

- [Hybrid Automata Modelling Language (HAML)](#hybrid-automata-modelling-language-haml)
      - [Version: 0.1.4](#version-014)
  - [Introduction](#introduction)
  - [Table of Contents](#table-of-contents)
  - [Definitions](#definitions)
      - [Hybrid Automata](#hybrid-automata)
      - [Hybrid Network](#hybrid-network)
      - [Ordinary Differential Equation](#ordinary-differential-equation)
  - [Specification](#specification)
    - [Includes](#includes)
  - [Schema](#schema)
    - [HAML Document Root](#haml-document-root)
      - [Fields](#fields)
      - [Example](#example)
    - [Network](#network)
      - [Fields](#fields-1)
      - [Example](#example-1)
    - [Automata](#automata)
      - [Fields](#fields-2)
      - [Example](#example-2)
    - [Variable Definition](#variable-definition)
      - [Fields](#fields-3)
      - [Example](#example-3)
    - [Variable Type](#variable-type)
      - [Enum Values](#enum-values)
    - [Location](#location)
      - [Fields](#fields-4)
      - [Example](#example-4)
    - [Transition](#transition)
      - [Fields](#fields-5)
      - [Example](#example-5)
    - [Function](#function)
      - [Fields](#fields-6)
      - [Example](#example-6)
    - [Initialisation](#initialisation)
      - [Fields](#fields-7)
      - [Example](#example-7)
    - [Instance](#instance)
      - [Fields](#fields-8)
      - [Example](#example-8)
    - [Program](#program)
      - [Example](#example-9)
    - [Formula](#formula)
      - [Example](#example-10)
    - [Codegen Configuration](#codegen-configuration)
      - [Fields](#fields-9)
      - [Example](#example-11)
    - [Execution Settings](#execution-settings)
      - [Fields](#fields-10)
      - [Example](#example-12)
    - [Logging Settings](#logging-settings)
      - [Fields](#fields-11)
      - [Example](#example-13)
    - [Parametrisation Method](#parametrisation-method)
      - [Enum Values](#enum-values-1)
    - [C Code Settings](#c-code-settings)
      - [Fields](#fields-12)
      - [Example](#example-14)
  - [Example documents](#example-documents)
    - [Water Heater](#water-heater)
  - [Revision History](#revision-history)


## Definitions

#### Hybrid Automata
A form of automata that supports both continuous and discrete parts.
Continuous aspects of the automata are specified through [ODEs](#ordinary-differential-equation), while discrete aspects are captured through locations and transitions between them.

#### Hybrid Network
A network of [Hybrid Automata](#hybrid-automata) that can be connected through their inputs and outputs in order to create a complete system.

#### Ordinary Differential Equation
Shortened to ODE, these are equations which describe how a continuous variable evolves over time by specifying its rate of change or gradient.


## Specification

This specification builds on top of the [YAML 1.2 specification](http://yaml.org/spec/1.2/spec.html).

All field names are **case sensitive**.
Fields which are marked as **required** must be present in the specification.
Otherwise, the field can be assumed to be optional, and the default value (if present) will be used.

The complete document may be split up into multiple individual files through the use of [Includes](#includes).

### Includes

HAML adds the concept of a `!include` tag, which allows for external files to have their content included into the specification.
The purpose of this tag is to allow for smaller and easier to understand specification files, as well as catering for code re-use if possible.

The `!include` tag in a HAML document acts as a "copy and paste" tool which purely inserts the content in the included file under the current node.
An included file does not need to be a valid HAML document, or even part of a HAML document, it can represent any aspect of the specification (e.g. a string).

The `!include` tag only takes a single argument, which is the location of the file to be included.
This location can be either be an absolute path (beginning with a `/`), or a relative path (beginning with any other character).
In each case, the location is relative to the file location where the `!include` tag is located.


## Schema

### HAML Document Root

The root object for the HAML Document.

#### Fields

| Name | Type | Description |
|---|---|---|
| name | String | **Required.** The name of this Hybrid Network. |
| system | [Network](#network) \| [Automata](#automata) | **Required.** The main item that describes the system. Which could be either a [Network](#network) or single [Automata](#automata). |
| codegenConfig | [Codegen Configuration](#codegen-configuration) | A list of settings available for the default code generation logic in this tool.<br/><br/> **Default:** A default instance of [Codegen Configuration](#codegen-configuration). |

#### Example

```yaml
name: heart

system:
  !include heart.yaml

codegenConfig:
  execution:
    stepSize: 0.00001
    simulationTime: 10
  logging:
    file: out.csv
  parametrisationMethod: COMPILE_TIME
  maximumInterTransitions: 1
  requireOneIntraTransitionPerTick: false
```

### Network

The object that captures a Hybrid Network, including mappings and instantiations.
A Network can instantiate further networks inside of it, to create a hierarchical structure.

#### Fields

| Name | Type | Description |
|---|---|---|
| inputs | Map[String, [Variable Type](#variable-type) \| [Variable Definition](#variable-definition)] | The variables that this Hybrid Network accepts as inputs. |
| outputs | Map[String, [Variable Type](#variable-type) \| [Variable Definition](#variable-definition)] | The variables that this Hybrid Network emits as outputs. |
| parameters | Map[String, [Variable Type](#variable-type) \| [Variable Definition](#variable-definition)] | The parameters that are available for configuration of this Hybrid Network. |
| definitions | Map[String, [Network](#network) \| [Automata](#automata)] | **Required.** A set of definitions of Hybrid Networks or Hybrid Automata that can be instantiated. |
| instances | Map[String, [Instance](#instance) \| String] | **Required.** A set of instances of previously defined Hybrid Networks or Hybrid Automata. |
| mappings | Map[String, [Formula](#formula)] | A set of mappings that determine the value of each output of this network, or input of each Instance. |


#### Example

```yaml
outputs:
  SA_v: REAL

definitions:
  Cell: !include cell.yaml
  Path: !include path.yaml

instances:
  !include cells.yaml
  !include paths.yaml

mappings:
  SA_v: SA.v
  !include mappings.yaml
```


### Automata

The object that captures a Hybrid Automata and its logic.

#### Fields

| Name | Type | Description |
|---|---|---|
| inputs | Map[String, [Variable Type](#variable-type) \| [Variable Definition](#variable-definition)] | The variables that this Hybrid Automata accepts as inputs. |
| outputs | Map[String, [Variable Type](#variable-type) \| [Variable Definition](#variable-definition)] | The variables that this Hybrid Automata emits as outputs. |
| parameters | Map[String, [Variable Type](#variable-type) \| [Variable Definition](#variable-definition)] | The parameters that are available for configuration of this Hybrid Automata. |
| locations | Map[String, [Location](#location)] | **Required.** The locations that exist inside this Hybrid Automata. |
| functions | Map[String, [Function](#function)] | A set of functions that exist inside this Hybrid Automata. |
| initialisation | [Initialisation](#initialisation) | **Required.** Sets the initialisation options for the Hybrid Automata (location, variable states, etc.). |

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

Information about a variable that exists within a Hybrid Automata.

#### Fields

| Name | Type | Description |
|---|---|---|
| type | [Variable Type](#variable-type) | **Required.** The type of the variable. |
| default | [Formula](#formula) | The default value for the variable. |
| delayableBy | [Formula](#formula) | An amount of time that this variable could possibly be delayed by. Used in code generation.<br/><br/> **Default:** `0` |

#### Example

```yaml
type: REAL
default: -8.7
```


### Variable Type

An **enum** that represents the type of a variable.

#### Enum Values

| Value | Description |
|---|---|
| `BOOLEAN` | A boolean variable. |
| `REAL` | A real-numbered variable. |
| `INTEGER` | An integer variable. |


### Location

A single location within a Hybrid Automata.

#### Fields

| Name | Type | Description |
|---|---|---|
| invariant | [Formula](#formula) | The invariant that exists on this location. For control to remain in this location the invariant must hold true.<br/><br/> **Default:** `true` |
| flow | Map[String, [Formula](#formula)] | The set of flow constraints that exist for each ODE, these constraints will transform the values of the variables while control remains in this location. |
| update | Map[String, [Formula](#formula)] | A set of discrete operations that are done while inside this location. |
| transitions | [Transition](#transition)[] | A set of transitions that exist out of this location. |

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

A transition to another location within a Hybrid Automata.

#### Fields

| Name | Type | Description |
|---|---|---|
| to | String | **Required.** The name of the destination location for this transition. |
| guard | [Formula](#formula) | The guard that protects when this transition is "active" and can be taken.<br/><br/> **Default:** `true` |
| update | Map[String, [Formula](#formula)] | A set of discrete operations that are done when this transition is taken. |

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

A function that exists within a Hybrid Automata and can be invoked.
The return type (if any) is automatically generated from the logic.

#### Fields

| Name | Type | Description |
|---|---|---|
| inputs | Map[String, [Variable Type](#variable-type) \| [Variable Definition](#variable-definition)] | The set of inputs that this function accepts. |
| logic | [Program](#program) | **Required.** The code that this function will perform when invoked. |

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

Initialisation of a Hybrid Automata, including its initial state and variable valuations.

#### Fields

| Name | Type | Description |
|---|---|---|
| state | String | **Required.** The initial state that the Hybrid Automata will start in. |
| valuations | Map[String, [Formula](#formula)] | The initial set of valuations for variables in the Hybrid Automata. |

#### Example

```yaml
state: q0
valuations:
  resting: true
```


### Instance

An instantiation of an [Automata](#automata) or [Network](#network).

#### Fields

| Name | Type | Description |
|---|---|---|
| type | String | **Required.** The previously declared definition that this instance instantiates. |
| parameters | Map[String, [Formula](#formula)] | The values of any parameters inside the previous declaration. Any parameters which do not have an entry here will inherit their default value (if any). |

#### Example

```yaml
type: Cell
parameters:
  C14: 20
  autorhythmic_rate: 100
```


### Program

A sequence of operations that are to be executed.
Programs allow for more advanced implementations than can be captured solely through Hybrid Automata.

A Program should be defined as a String.
There is no need for semicolons at the end of each line, nor is there a need to declare and initialise variables, all internal variables and their types will automatically be inferred.

Each line may be one of the following:

| Name | Description | Example |
|---|---|---|
| Statement | A single [Formula](#formula). | `x + 5` |
| Assignment | An assignment of a [Formula](#formula) to a variable. | `y = x + 5` |
| Return | Return some [Formula](#formula) for a [Function](#function) | `return x + 5` |
| If Statement | Allows for branching based on some [Formula](#formula) | `if (x < 5) {`<br/>&nbsp;&nbsp;&nbsp;&nbsp;`y = x + 5`<br/>`}` |
| ElseIf Statement | Allows for branching based on some [Formula](#formula) | `elseif (x < 10) {`<br/>&nbsp;&nbsp;&nbsp;&nbsp;`y = x + 10`<br/>`}` |
| Else Statement | Allows for branching | `else {`<br/>&nbsp;&nbsp;&nbsp;&nbsp;`y = x + 15`<br/>`}` |
| For Statement | Allows for loops between some bounds (inclusive) | `for (i in 0 to 3) {`<br/>&nbsp;&nbsp;&nbsp;&nbsp;`x = x + i`<br/>`}` |
| Break | Allows for exiting of loops before their natural end | `break` |

#### Example

```c
if(theta >= v_n_R_max) {
  return 4.03947
}

return 0.29*exp(62.89*theta) + 0.70*exp(-10.99*theta)
```


### Formula

A boolean or algebraic formula that can be used to either represent a boolean, real, or integer result.

A Formula is implemented as a String, and allows for a subset of math operations to be used within.
Custom functions can also be called, with any arbitrary number of parameters.
Any real number, integer number, the values `true` and `false`, and any variables, can be used as operands for any operation.
Parentheses (`(` and `)`) can be used to force operation order, as standard precedence rules are used.

The operations allowed in a Formula are as follows:

| Name | Symbol | Name | Symbol |
|---|---|---|---|
| AND | `&&` | OR | `\|\|` |
| NOT | `!` | | |
| Greater Than or Equal | `>=` | Greater Than | `>` |
| Less Than or Equal | `<=` | Less Than | `<` |
| Equal | `==` | Not Equal | `!=` |
| Plus | `+` | Minus / Negative | `-` |
| Multiply | `*` | Divide | `/` |
| Power | `pow` | Square Root | `sqrt` |
| Exponential | `exp` | Natural Log | `ln` |
| Sine | `sin` | Cosine | `cos` |
| Tangent | `tan` | | |
| Floor | `floor` | Ceil | `ceil` |

Additionally, several constants are provided in the spec, namely:

| Name | Symbol |
|---|---|
| Pi | `PI` |

#### Example

```c
v < V_O - 80.1 * sqrt(theta)
```


### Codegen Configuration

A set of options that determine how the generate code will look and behave.

#### Fields

| Name | Type | Description |
|---|---|---|
| indentSize | Int | The indentation size (in spaces) for the generated code. Use negative numbers for tabs.<br/><br/> **Default:** `4` |
| execution | [Execution Settings](#execution-settings) | The settings for the execution properties of the generated code.<br/><br/> **Default:** A default instance of [Execution Settings](#execution-settings). |
| logging | [Logging Settings](#logging-settings) | The settings for the logging properties of the generated code.<br/><br/> **Default:** A default instance of [Logging Settings](#logging-settings). |
| parametrisationMethod | [Parametrisation Method](#parametrisation-method) | The method to use for parametrisation when code is generated.<br/><br/> **Default:** `COMPILE_TIME` |
| maximumInterTransitions | Int | The maximum number of inter-location transitions that can be taken within each "step". In Hybrid Automata semantics these transitions should be instantaneous and this aims to replicate that to some degree.<br/><br/> **Default:** `1` |
| requireOneIntraTransitionPerTick | Boolean | Whether or not to require an intra-location transition (i.e. ODEs) within each "step". The evolution of ODEs is the only aspect of Hybrid Automata that should take any time.<br/><br/> **Default:** `false` |
| ccodeSettings | [C Code Settings](#c-code-settings) | Settings specific to C code generation.<br/><br/> **Default:** A default instance of [C Settings](#c-settings). |

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
ccodeSettings:
  additionalHeaders:
    - math.h
    - annot.h
  loopAnnotation: ANNOT_MAXITER({bound});
```


### Execution Settings

A set of options that determine the execution time and fidelity of the generated code.

#### Fields

| Name | Type | Description |
|---|---|---|
| stepSize | Double | The step size that is used for discretising the ODEs during execution, in seconds.<br/><br/> **Default:** `0.001` |
| simulationTime | Double | The time that will be simulated when the generated code is executed, in seconds.<br/><br/> **Default:** `10.0` |

#### Example

```yaml
stepSize: 0.00001
simulationTime: 10
```


### Logging Settings

A set of options that determine which information is logged when the generated code is executed.

#### Fields

| Name | Type | Description |
|---|---|---|
| enable | Boolean | Whether or not to enable logging of outputs.<br/><br/> **Default:** `true` |
| interval | Double | The interval at which to output log results to the file. For best results this should be an integer multiple of the step size.<br/><br/> **Default:** The same as the value of `stepSize` declared in [Execution Settings](#execution-settings). |
| file | String | The file where the logging output should be placed.<br/><br/> **Default:** `out.csv` |
| fields | String[] | The list of fields to output when logging.<br/><br/>**Default:** Every output variable of the top level system. |

#### Example

```yaml
interval: 0.005
file: out.csv
fields:
  - SA_v
  - RA_v
  - OS_v
  - Fast_v
  - AV_v
  - His_v
  - RBB_v
  - RVA_v
  - RV_v
```


### Parametrisation Method

An **enum** that represents the method used for parametrising the Hybrid Automata Definitions.

#### Enum Values

| Value | Description |
|---|---|
| `COMPILE_TIME` | Parameters will be set at the point of code generation. A file will be created for each [Instance](#instance) which results in a larger code size, but potentially faster execution. |
| `RUN_TIME` | Parameters will be set dynamically when the generated code is executed. Only one file will be created for each [Automata](#automata) and [Network](#network) which results in smaller code size, but likely slower execution. |



### C Code Settings

A set of options that are specific to C code generation.

#### Fields

| Name | Type | Description |
|---|---|---|
| additionalHeaders | String[] | A list of libraries that should also be included in generated files, may be used by custom functions or loop annotations.<br/><br/>**Default:** Empty |
| loopAnnotation | String | In C code generation, all loops will be annotated with this string for declaring bounds, replacing `{bound}` with the actual loop bounds.<br/><br/> **Default:** `NULL` |

#### Example

```yaml
additionalHeaders:
  - math.h
  - annot.h
loopAnnotation: ANNOT_MAXITER({bound});
```


## Example documents

Some example documents are provided below.

### Water Heater

```yaml
name: water_heater

system:
  outputs:
    temperature: REAL

  definitions:
    Watertank:
      inputs:
        burnerOn: BOOLEAN
        burnerOff: BOOLEAN
      outputs:
        temperature: REAL
      locations:
        t1:
          invariant: temperature >= 20 && temperature < 100 && !burnerOff
          flow:
            temperature: 0.075 * (150 - temperature)
          transitions:
            - to: t2
              guard: temperature == 100 && !burnerOff
            - to: t3
              guard: burnerOff
        t2:
          invariant: '!burnerOff'
          transitions:
            - to: t3
              guard: burnerOff
        t3:
          invariant: temperature > 20 && temperature <= 100 && !burnerOn
          flow:
            temperature: -0.075 * temperature
          transitions:
            - to: t4
              guard: temperature == 20 && !burnerOn
            - to: t1
              guard: burnerOn
        t4:
          invariant: '!burnerOn'
          transitions:
            - to: t1
              guard: burnerOn
      initialisation:
        state: t1
        valuations:
          temperature: 20

    Burner:
      outputs:
        burnerOn: BOOLEAN
        burnerOff: BOOLEAN
      parameters:
        cycleTime:
          type: REAL
          default: 20
      locations:
        b1:
          invariant: y < cycleTime
          flow:
            y: 1
          update:
            burnerOn: true
            burnerOff: false
          transitions:
            - to: b2
              guard: y == cycleTime
              update:
                y: 0
        b2:
          invariant: y < cycleTime
          flow:
            y: 1
          update:
            burnerOn: false
            burnerOff: true
          transitions:
            - to: b1
              guard: y == cycleTime
              update:
                y: 0
      initialisation:
        state: b1
        valuations:
          y: 0

  instances:
    Watertank: Watertank
    Burner: Burner


  mappings:
    temperature: Watertank.temperature

    Watertank.burnerOn: Burner.burnerOn
    Watertank.burnerOff: Burner.burnerOff

codegenConfig:
  execution:
    stepSize: 0.0001
    simulationTime: 100
  logging:
    file: out.csv
```


## Revision History

| Version | Date | Notes |
|---|---|---|
| 0.1.4 | 2020-02-25 | Added support for more functions: `Power`, `Natural Log`, `Floor`, `Ceil` |
| 0.1.3 | 2019-04-26 | Added support for trigonometric functions and constants |
| 0.1.2 | 2019-01-31 | Added support for `Integer` types of variables |
| 0.1.1 | 2018-02-08 | Updated to support hierarchy of Networks |
| 0.1.0 | 2018-01-20 | Initial release |
