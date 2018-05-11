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
        var name: String,

        @JacksonXmlElementWrapper(useWrapping = false)
        var units: List<Units>?,

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "component")
        var components: List<Component>?
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(namespace = "http://www.cellml.org/cellml/1.0", localName = "units")
data class Units(
        @JacksonXmlProperty(isAttribute = true)
        var name: String,

        @JacksonXmlProperty(isAttribute = true, localName = "base_units")
        var baseUnits: String = "no",

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "unit")
        var subunits: List<Unit>?
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(namespace = "http://www.cellml.org/cellml/1.0", localName = "unit")
data class Unit(
        @JacksonXmlProperty(isAttribute = true)
        var units: String,

        @JacksonXmlProperty(isAttribute = true)
        var offset: Double = 0.0,

        @JacksonXmlProperty(isAttribute = true)
        var prefix: Int = 0,

        @JacksonXmlProperty(isAttribute = true)
        var exponent: Double = 1.0,

        @JacksonXmlProperty(isAttribute = true)
        var multiplier: Double = 1.0
) {
    @JsonCreator
    constructor(units: String, offset: Double = 0.0,
                prefix: Any?, exponent: Double = 0.0,
                multiplier: Double = 0.0): this(units, offset, 0, exponent, multiplier) {
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
        var name: String,

        @JacksonXmlElementWrapper(useWrapping = false)
        var units: List<Units>?,

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "variable")
        var variables: List<Variable>?,

        /*@JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "reaction")
        var reactions: List<Reaction>?*/

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(namespace = "http://www.w3.org/1998/Math/MathML", localName = "math")
        var maths: List<Math>?
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(namespace = "http://www.cellml.org/cellml/1.0", localName = "variable")
data class Variable(
        @JacksonXmlProperty(isAttribute = true)
        var name: String,

        @JacksonXmlProperty(isAttribute = true)
        var units: String,

        @JacksonXmlProperty(isAttribute = true, localName = "initial_value")
        var initialValue: Double = 0.0,

        @JacksonXmlProperty(isAttribute = true, localName = "public_interface")
        var publicInterface: InterfaceType = InterfaceType.NONE,

        @JacksonXmlProperty(isAttribute = true, localName = "private_interface")
        var privateInterface: InterfaceType = InterfaceType.NONE
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
