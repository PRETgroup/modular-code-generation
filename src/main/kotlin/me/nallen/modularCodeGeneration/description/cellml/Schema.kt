package me.nallen.modularcodegeneration.description.cellml

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonMerge
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import java.util.*
import kotlin.math.pow


@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(namespace = "http://www.cellml.org/cellml/1.0", localName = "model")
class Model constructor(
        @JacksonXmlProperty(isAttribute = true)
        val name: String
) {
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty
    @JsonMerge
    val units: List<Units>? = null

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "component")
    @JsonMerge
    val components: List<Component>? = null

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "connection")
    @JsonMerge
    val connections: List<Connection>? = null

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "group")
    @JsonMerge
    val groups: List<Group>? = null

    override fun toString(): String {
        return "Model(name='$name', units=$units, components=$components, connections=$connections, groups=$groups)"
    }

}

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(namespace = "http://www.cellml.org/cellml/1.0", localName = "units")
class Units constructor(
        @JacksonXmlProperty(isAttribute = true)
        val name: String,

        @JacksonXmlProperty(isAttribute = true, localName = "base_units")
        val baseUnits: String = "no"
) {
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "unit")
    @JsonMerge
    var subunits: List<Unit>? = null

    override fun toString(): String {
        return "Units(name='$name', baseUnits='$baseUnits', subunits=$subunits)"
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(namespace = "http://www.cellml.org/cellml/1.0", localName = "unit")
class Unit constructor(
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

    override fun toString(): String {
        return "Unit(units='$units', offset=$offset, prefix=$prefix, exponent=$exponent, multiplier=$multiplier)"
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(namespace = "http://www.cellml.org/cellml/1.0", localName = "component")
class Component constructor(
        @JacksonXmlProperty(isAttribute = true)
        val name: String
) {
    @JacksonXmlElementWrapper(useWrapping = false)
    @JsonMerge
    val units: List<Units>? = null

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "variable")
    @JsonMerge
    val variables: List<Variable>? = null

    /*@JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "reaction")
    @JsonMerge
    var reactions: List<Reaction>?*/

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(namespace = "http://www.w3.org/1998/Math/MathML", localName = "math")
    @JsonMerge
    val maths: List<Math>? = null

    override fun toString(): String {
        return "Component(name='$name', units=$units, variables=$variables, maths=$maths)"
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(namespace = "http://www.cellml.org/cellml/1.0", localName = "variable")
class Variable constructor(
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
) {
    override fun toString(): String {
        return "Variable(name='$name', units='$units', initialValue=$initialValue, publicInterface=$publicInterface, privateInterface=$privateInterface)"
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(namespace = "http://www.cellml.org/cellml/1.0", localName = "connection")
class Connection constructor(
        @JacksonXmlProperty(localName = "map_components")
        val components: MapComponents,

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "map_variables")
        @JsonMerge
        val variables: List<MapVariables>
) {
    override fun toString(): String {
        return "Connection(components=$components, variables=$variables)"
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(namespace = "http://www.cellml.org/cellml/1.0", localName = "map_components")
class MapComponents constructor(
        @JacksonXmlProperty(isAttribute = true, localName = "component_1")
        val component1: String,

        @JacksonXmlProperty(isAttribute = true, localName = "component_2")
        val component2: String
) {
    override fun toString(): String {
        return "MapComponents(component1='$component1', component2='$component2')"
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(namespace = "http://www.cellml.org/cellml/1.0", localName = "map_variables")
class MapVariables constructor(
        @JacksonXmlProperty(isAttribute = true, localName = "variable_1")
        val variable1: String,

        @JacksonXmlProperty(isAttribute = true, localName = "variable_2")
        val variable2: String
) {
    override fun toString(): String {
        return "MapVariables(variable1='$variable1', variable2='$variable2')"
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(namespace = "http://www.cellml.org/cellml/1.0", localName = "group")
class Group constructor(
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "relationship_ref")
        @JsonMerge
        val relationships: List<RelationshipRef>,

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "component_ref")
        @JsonMerge
        val components: List<ComponentRef>
) {
    override fun toString(): String {
        return "Group(relationships=$relationships, components=$components)"
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(namespace = "http://www.cellml.org/cellml/1.0", localName = "relationship_ref")
class RelationshipRef constructor(
        @JacksonXmlProperty(isAttribute = true, localName = "name")
        val name: String?,

        @JacksonXmlProperty(isAttribute = true, localName = "relationship")
        val relationship: RelationshipType?
) {
    override fun toString(): String {
        return "RelationshipRef(name=$name, relationship=$relationship)"
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(namespace = "http://www.cellml.org/cellml/1.0", localName = "component_ref")
class ComponentRef constructor(
        @JacksonXmlProperty(isAttribute = true, localName = "component")
        val component: String
) {
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "component_ref")
    @JsonMerge
    val components: List<ComponentRef>? = null

    override fun toString(): String {
        return "ComponentRef(component='$component', components=$components)"
    }
}



enum class InterfaceType {
    @JsonProperty("none")
    NONE,

    @JsonProperty("in")
    IN,

    @JsonProperty("out")
    OUT
}

enum class RelationshipType {
    @JsonProperty("containment")
    CONTAINMENT,

    @JsonProperty("encapsulation")
    ENCAPSULATION
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
            return this.name == other.name
        }
        else if(this is BooleanUnit && other is BooleanUnit) {
            return true
        }
        else if(this is CompositeUnit && other is CompositeUnit) {
            if(this.baseUnits.count { it.exponent != 0.0 } != other.baseUnits.count { it.exponent != 0.0 })
                return false

            for(unit in this.baseUnits.filter { it.exponent != 0.0 }) {
                if(!other.baseUnits.any { it.baseUnit.name == unit.baseUnit.name && it.exponent == unit.exponent })
                    return false
            }

            return true
        }
        else if(this is CompositeUnit && other is BaseUnit) {
            if(this.baseUnits.count { it.exponent != 0.0 } != 1 || this.baseUnits.first { it.exponent != 0.0 }.exponent != 1.0)
                return false

            return this.baseUnits[0].baseUnit.name == other.name
        }
        else if(this is BaseUnit && other is CompositeUnit) {
            if(other.baseUnits.count { it.exponent != 0.0 } != 1 || other.baseUnits.first { it.exponent != 0.0 }.exponent != 1.0)
                return false

            return other.baseUnits[0].baseUnit.name == this.name
        }
        else {
            return false
        }
    }

    fun getDifferenceTo(other: SimpleUnit): Pair<Double, Double>? {
        if(!this.canMapTo(other))
            return null

        if(this !is CompositeUnit || other !is CompositeUnit)
            return Pair(1.0, 0.0)

        return Pair(other.multiply / this.multiply, other.offset - this.offset)
    }

    fun createToPowerOf(power: Double): SimpleUnit {
        val unitList = ArrayList<BaseUnitInstance>()
        var multiply = 1.0
        var offset = 0.0

        if(this is BaseUnit) {
            unitList.add(BaseUnitInstance(this, 1.0 * power))
        }
        else if(this is CompositeUnit) {
            for((baseUnit, exponent) in this.baseUnits) {
                if(unitList.any { it.baseUnit.name == baseUnit.name }) {
                    unitList.first { it.baseUnit.name == baseUnit.name }.exponent += exponent * power
                }
                else {
                    unitList.add(BaseUnitInstance(baseUnit, exponent * power))
                }
            }

            multiply = this.multiply?.pow(power)
            offset = this.offset
        }

        return CompositeUnit(unitList, multiply, offset)
    }

    fun createMultiplication(other: SimpleUnit): SimpleUnit {
        val unitList = ArrayList<BaseUnitInstance>()
        var multiply = 1.0
        var offset = 0.0
        for(units in listOf(this, other)) {
            if(units is BaseUnit) {
                if(unitList.any { it.baseUnit.name == units.name }) {
                    unitList.first { it.baseUnit.name == units.name }.exponent += 1.0
                }
                else {
                    unitList.add(BaseUnitInstance(units, 1.0))
                }
            }
            else if(units is CompositeUnit) {
                for((baseUnit, exponent) in units.baseUnits) {
                    if(exponent != 0.0) {
                        if(unitList.any { it.baseUnit.name == baseUnit.name }) {
                            unitList.first { it.baseUnit.name == baseUnit.name }.exponent += exponent
                        }
                        else {
                            unitList.add(BaseUnitInstance(baseUnit, exponent))
                        }
                    }
                }

                multiply *= units.multiply
            }
        }

        return CompositeUnit(unitList, multiply, offset)
    }
}
class BooleanUnit(): SimpleUnit()
data class BaseUnit(val name: String): SimpleUnit()
data class BaseUnitInstance(val baseUnit: BaseUnit, var exponent: Double = 1.0)
data class CompositeUnit(val baseUnits: List<BaseUnitInstance> = listOf(), val multiply: Double = 1.0, val offset: Double = 0.0): SimpleUnit()
