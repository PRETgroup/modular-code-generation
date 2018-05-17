package me.nallen.modularCodeGeneration.description.cellml

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText
import java.util.*

@JsonDeserialize(using = MathDeserializer::class)
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(namespace = "http://www.w3.org/1998/Math/MathML", localName = "math")
data class Math(
        val items: ArrayList<MathItem> = ArrayList()
)

class MathDeserializer(vc: Class<*>? = null) : StdDeserializer<Math>(vc) {
    override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): Math {
        if (p != null) {
            val mathObject = Math()
            val parsedObject = parseObject(p)

            for(item in parsedObject) {
                mathObject.items.add(createMathItem(item))
            }

            return mathObject
        }

        return Math()
    }

    private fun parseObject(p: JsonParser): ArrayList<Node> {
        val fields = ArrayList<Node>()
        var fieldName: String? = null


        var token = p.currentToken()

        if(token != JsonToken.START_OBJECT)
            throw Exception("Invalid Schema detected")

        do {
            token = p.nextToken()

            if(token == JsonToken.FIELD_NAME)
                fieldName = p.valueAsString

            if(needsStoring(token)) {
                if(fieldName == null)
                    throw Exception("Invalid Schema detected")

                fields.add(Node(fieldName, getTokenValueFromParser(token, p)))

                fieldName = null
            }

            if(token == JsonToken.END_OBJECT) {
                break;
            }
        }
        while(true)

        return fields
    }

    data class Node(val tag: String, val value: Any?)

    private fun needsStoring(token: JsonToken): Boolean {
        return when(token) {
            JsonToken.START_OBJECT -> true
            JsonToken.VALUE_NULL -> true
            JsonToken.VALUE_STRING -> true
            JsonToken.VALUE_NUMBER_INT -> true
            JsonToken.VALUE_NUMBER_FLOAT -> true
            JsonToken.VALUE_FALSE -> true
            JsonToken.VALUE_TRUE -> true
            else -> false
        }
    }

    private fun getTokenValueFromParser(token: JsonToken, p: JsonParser): Any? {
        return when(token) {
            JsonToken.START_OBJECT -> parseObject(p)
            JsonToken.VALUE_NULL -> null
            JsonToken.VALUE_STRING -> p.valueAsString
            JsonToken.VALUE_NUMBER_INT -> p.valueAsInt
            JsonToken.VALUE_NUMBER_FLOAT -> p.valueAsDouble
            JsonToken.VALUE_FALSE -> p.valueAsBoolean
            JsonToken.VALUE_TRUE -> p.valueAsBoolean
            else -> false
        }
    }

    private fun createMathItem(node: Node): MathItem {
        return when(node.tag) {
            "apply" -> createApply(node.value as List<Any?>)
            "ci" -> createCi(node.value)
            "cn" -> createCn(node.value)
            "bvar" -> createBvar(node.value)
            else -> throw Exception("Unknown tag <" + node.tag + "> in math")
        }
    }

    private fun createApply(items: List<Any?>): Apply {
        var id: String? = null
        var operation: Operation? = null
        val arguments = ArrayList<MathItem>()

        for(item in items) {
            if(item is Node) {
                if(item.tag == "id") {
                    id = item.value as String
                }
                else if(Operation.isValidIdentifier(item.tag)) {
                    if(operation != null)
                        throw Exception("Multiple operators inside <apply> element")

                    operation = Operation.getForIdentifier(item.tag)
                }
                else {
                    arguments.add(createMathItem(item))
                }
            }
        }

        if(operation == null)
            throw Exception("No operator found for <apply> element")

        return Apply(id, operation, arguments)
    }

    private fun createCi(value: Any?): Ci {
        if(value is String)
            return Ci(value.trim())

        else if(value is List<Any?>) {
            throw NotImplementedError()
        }

        throw Exception("Invalid <ci> provided - missing variable")
    }

    private fun createCn(value: Any?): Cn {
        if(value is List<Any?>) {
            if(!value.any { it is Node && it.tag == "units" })
                throw Exception("Invalid <cn> provided - missing 'units' argument")

            val unitsNode = (value.first{ it is Node && it.tag == "units" } as Node)

            if(unitsNode.value !is String)
                throw Exception("Invalid <cn> provided - invalid 'units' argument")

            if(!value.any { it is Node && it.tag == "" })
                throw Exception("Invalid <cn> provided - missing value")

            val valueNode = (value.first{ it is Node && it.tag == "" } as Node)

            val fixedValueNode =
            if(valueNode.value is String && valueNode.value.toDoubleOrNull() != null) {
                Node(valueNode.tag, valueNode.value.toDouble())
            }
            else {
                valueNode
            }

            if(fixedValueNode.value !is Number) {
                throw Exception("Invalid <cn> provided - invalid value")
            }

            return Cn(unitsNode.value, fixedValueNode.value.toDouble())
        }

        throw Exception("Invalid <cn> argument provided")
    }

    private fun createBvar(value: Any?): Bvar {
        if(value is List<Any?>) {
            if(!value.any { it is Node && it.tag == "ci" })
                throw Exception("Invalid <bvar> provided - missing 'ci' field")

            val variableNode = (value.first{ it is Node && it.tag == "ci" } as Node)

            if(value.any { it is Node && it.tag == "degree" })
                throw NotImplementedError()

            return Bvar(createCi(variableNode.value))
        }

        throw Exception("Invalid <bvar> argument provided")
    }
}

sealed class MathItem {
    abstract fun generateString(): String
    abstract fun calculateUnits(): SimpleUnit
}

data class Apply(
        val id: String?,
        val operation: Operation,
        val arguments: List<MathItem> = ArrayList()
): MathItem() {
    override fun calculateUnits(): SimpleUnit {
        return when(operation) {
            Operation.EQ -> TODO()
            Operation.NEQ -> TODO()
            Operation.GT -> TODO()
            Operation.LT -> TODO()
            Operation.GEQ -> TODO()
            Operation.LEQ -> TODO()
            Operation.PLUS -> TODO()
            Operation.MINUS -> TODO()
            Operation.TIMES -> TODO()
            Operation.DIVIDE -> TODO()
            Operation.POWER -> TODO()
            Operation.ROOT -> TODO()
            Operation.ABS -> TODO()
            Operation.EXP -> TODO()
            Operation.LN -> TODO()
            Operation.LOG -> TODO()
            Operation.FLOOR -> TODO()
            Operation.CEILING -> TODO()
            Operation.FACTORIAL -> TODO()
            Operation.AND -> TODO()
            Operation.OR -> TODO()
            Operation.XOR -> TODO()
            Operation.NOT -> TODO()
            Operation.DIFF -> TODO()
            Operation.SIN -> TODO()
            Operation.COS -> TODO()
            Operation.TAN -> TODO()
            Operation.SEC -> TODO()
            Operation.CSC -> TODO()
            Operation.COT -> TODO()
            Operation.SINH -> TODO()
            Operation.COSH -> TODO()
            Operation.TANH -> TODO()
            Operation.SECH -> TODO()
            Operation.CSCH -> TODO()
            Operation.COTH -> TODO()
            Operation.ARCSIN -> TODO()
            Operation.ARCCOS -> TODO()
            Operation.ARCTAN -> TODO()
            Operation.ARCCOSH -> TODO()
            Operation.ARCCOT -> TODO()
            Operation.ARCCOTH -> TODO()
            Operation.ARCCSC -> TODO()
            Operation.ARCCSCH -> TODO()
            Operation.ARCSEC -> TODO()
            Operation.ARCSECH -> TODO()
            Operation.ARCSINH -> TODO()
            Operation.ARCTANH -> TODO()
        }
    }

    override fun generateString(): String {
        return when(operation) {
            Operation.EQ -> TODO()
            Operation.NEQ -> TODO()
            Operation.GT -> TODO()
            Operation.LT -> TODO()
            Operation.GEQ -> TODO()
            Operation.LEQ -> TODO()
            Operation.PLUS -> TODO()
            Operation.MINUS -> TODO()
            Operation.TIMES -> TODO()
            Operation.DIVIDE -> TODO()
            Operation.POWER -> TODO()
            Operation.ROOT -> TODO()
            Operation.ABS -> TODO()
            Operation.EXP -> TODO()
            Operation.LN -> TODO()
            Operation.LOG -> TODO()
            Operation.FLOOR -> TODO()
            Operation.CEILING -> TODO()
            Operation.FACTORIAL -> TODO()
            Operation.AND -> TODO()
            Operation.OR -> TODO()
            Operation.XOR -> TODO()
            Operation.NOT -> TODO()
            Operation.DIFF -> TODO()
            Operation.SIN -> TODO()
            Operation.COS -> TODO()
            Operation.TAN -> TODO()
            Operation.SEC -> TODO()
            Operation.CSC -> TODO()
            Operation.COT -> TODO()
            Operation.SINH -> TODO()
            Operation.COSH -> TODO()
            Operation.TANH -> TODO()
            Operation.SECH -> TODO()
            Operation.CSCH -> TODO()
            Operation.COTH -> TODO()
            Operation.ARCSIN -> TODO()
            Operation.ARCCOS -> TODO()
            Operation.ARCTAN -> TODO()
            Operation.ARCCOSH -> TODO()
            Operation.ARCCOT -> TODO()
            Operation.ARCCOTH -> TODO()
            Operation.ARCCSC -> TODO()
            Operation.ARCCSCH -> TODO()
            Operation.ARCSEC -> TODO()
            Operation.ARCSECH -> TODO()
            Operation.ARCSINH -> TODO()
            Operation.ARCTANH -> TODO()
        }
    }
}

sealed class MathValue : MathItem()

data class Cn(
        val units: String,
        val value: Double
): MathValue() {
    override fun calculateUnits(): SimpleUnit {
        TODO()
    }

    override fun generateString(): String {
        return value.toString()
    }
}

data class Ci(
        val name: String
): MathValue() {
    override fun calculateUnits(): SimpleUnit {
        TODO()
    }

    override fun generateString(): String {
        TODO()
    }
}

data class Bvar(
        val variable: Ci,
        val degree: Degree? = null
): MathItem() {
    override fun calculateUnits(): SimpleUnit {
        TODO()
    }

    override fun generateString(): String {
        TODO()
    }
}

data class Degree(
        val order: MathValue
): MathItem() {
    override fun calculateUnits(): SimpleUnit {
        val orderUnits = order.calculateUnits()

        if(orderUnits !is CompositeUnit || orderUnits.baseUnits.isNotEmpty())
            throw Exception("<degree> requires child to be dimensionless")

        return orderUnits
    }

    override fun generateString(): String {
        return order.generateString()
    }
}

enum class Operation {
    EQ, NEQ, GT, LT, GEQ, LEQ,
    PLUS, MINUS, TIMES, DIVIDE, POWER, ROOT, ABS, EXP, LN, LOG, FLOOR, CEILING, FACTORIAL,
    AND, OR, XOR, NOT,
    DIFF,
    SIN, COS, TAN, SEC, CSC, COT, SINH, COSH, TANH, SECH, CSCH, COTH,
    ARCSIN, ARCCOS, ARCTAN, ARCCOSH, ARCCOT, ARCCOTH, ARCCSC, ARCCSCH, ARCSEC, ARCSECH, ARCSINH, ARCTANH;

    companion object Factory {
        fun getForIdentifier(name: String): Operation? {
            return when(name) {
                "eq" -> EQ
                "neq" -> NEQ
                "gt" -> GT
                "lt" -> LT
                "geq" -> GEQ
                "leq" -> LEQ
                "plus" -> PLUS
                "minus" -> MINUS
                "times" -> TIMES
                "divide" -> DIVIDE
                "power" -> POWER
                "root" -> ROOT
                "abs" -> ABS
                "exp" -> EXP
                "ln" -> LN
                "log" -> LOG
                "floor" -> FLOOR
                "ceiling" -> CEILING
                "factorial" -> FACTORIAL
                "and" -> AND
                "or" -> OR
                "xor" -> XOR
                "not" -> NOT
                "diff" -> DIFF
                else -> null
            }
        }

        fun isValidIdentifier(name: String): Boolean {
            return getForIdentifier(name) != null
        }
    }
}