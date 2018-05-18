package me.nallen.modularCodeGeneration.description.cellml

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty


@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(namespace = "http://www.cellml.org/cellml/1.0", localName = "model")
data class Model(
        @JacksonXmlProperty(isAttribute = true)
        val name: String,

        @JacksonXmlElementWrapper(useWrapping = false)
        val units: List<Units>?,

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "component")
        val components: List<Component>?,

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "connection")
        val connections: List<Connection>?
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(namespace = "http://www.cellml.org/cellml/1.0", localName = "units")
data class Units(
        @JacksonXmlProperty(isAttribute = true)
        val name: String,

        @JacksonXmlProperty(isAttribute = true, localName = "base_units")
        val baseUnits: String = "no",

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "unit")
        val subunits: List<Unit>?
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(namespace = "http://www.cellml.org/cellml/1.0", localName = "unit")
data class Unit(
        @JacksonXmlProperty(isAttribute = true)
        val units: String,

        @JacksonXmlProperty(isAttribute = true)
        val offset: Double = 0.0,

        @JacksonXmlProperty(isAttribute = true)
        var prefix: Int = 0,

        @JacksonXmlProperty(isAttribute = true)
        val exponent: Double = 1.0,

        @JacksonXmlProperty(isAttribute = true)
        val multiplier: Double = 1.0
) {
    @JsonCreator
    constructor(units: String, offset: Double = 0.0,
                prefix: Any?, exponent: Double = 1.0,
                multiplier: Double = 1.0): this(units, offset, 0, exponent, multiplier) {
        if(prefix is Int)
            this.prefix = prefix
        else if(prefix is String) {
            val convertedPrefix = prefix.toIntOrNull()
            this.prefix = when(convertedPrefix) {
                null -> prefixToPowerOfTen(prefix)
                else -> convertedPrefix
            }
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(namespace = "http://www.cellml.org/cellml/1.0", localName = "component")
data class Component(
        @JacksonXmlProperty(isAttribute = true)
        val name: String,

        @JacksonXmlElementWrapper(useWrapping = false)
        val units: List<Units>?,

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "variable")
        val variables: List<Variable>?,

        /*@JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "reaction")
        var reactions: List<Reaction>?*/

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(namespace = "http://www.w3.org/1998/Math/MathML", localName = "math")
        val maths: List<Math>?
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(namespace = "http://www.cellml.org/cellml/1.0", localName = "variable")
data class Variable(
        @JacksonXmlProperty(isAttribute = true)
        val name: String,

        @JacksonXmlProperty(isAttribute = true)
        val units: String,

        @JacksonXmlProperty(isAttribute = true, localName = "initial_value")
        val initialValue: Double = 0.0,

        @JacksonXmlProperty(isAttribute = true, localName = "public_interface")
        val publicInterface: InterfaceType = InterfaceType.NONE,

        @JacksonXmlProperty(isAttribute = true, localName = "private_interface")
        val privateInterface: InterfaceType = InterfaceType.NONE
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(namespace = "http://www.cellml.org/cellml/1.0", localName = "connection")
data class Connection(
        @JacksonXmlProperty(localName = "map_components")
        val components: MapComponents,

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "map_variables")
        val variables: List<MapVariables>
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(namespace = "http://www.cellml.org/cellml/1.0", localName = "map_components")
data class MapComponents(
        @JacksonXmlProperty(isAttribute = true, localName = "component_1")
        val component1: String,

        @JacksonXmlProperty(isAttribute = true, localName = "component_2")
        val component2: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(namespace = "http://www.cellml.org/cellml/1.0", localName = "map_variables")
data class MapVariables(
        @JacksonXmlProperty(isAttribute = true, localName = "variable_1")
        val variable1: String,

        @JacksonXmlProperty(isAttribute = true, localName = "variable_2")
        val variable2: String
)




enum class InterfaceType {
    @JsonProperty("none")
    NONE,

    @JsonProperty("in")
    IN,

    @JsonProperty("out")
    OUT
}

fun prefixToPowerOfTen(prefix: String): Int {
    return when(prefix) {
        "" -> 0
        "yotta" -> 24
        "zetta" -> 21
        "exa" -> 18
        "peta" -> 15
        "tera" -> 12
        "giga" -> 9
        "mega" -> 6
        "kilo" -> 3
        "hecto" -> 2
        "deka" -> 1
        "deci" -> -1
        "centi" -> -2
        "milli" -> -3
        "micro" -> -6
        "nano" -> -9
        "pico" -> -12
        "femto" -> -15
        "atto" -> -18
        "zepto" -> -21
        "yocto" -> -24
        else -> throw IllegalArgumentException("Invalid prefix provided: " + prefix)
    }
}

open class SimpleUnit {
    fun canMapTo(other: SimpleUnit): Boolean {
        if(this is BaseUnit && other is BaseUnit) {
            return true
        }
        else if(this is CompositeUnit && other is CompositeUnit) {
            return true
        }
        else {
            return false
        }
    }
}
data class BaseUnit(val name: String): SimpleUnit()
data class BaseUnitInstance(val baseUnit: BaseUnit, var exponent: Double = 1.0)
data class CompositeUnit(val baseUnits: List<BaseUnitInstance> = listOf(), val multiply: Double = 1.0, val offset: Double = 0.0): SimpleUnit()
