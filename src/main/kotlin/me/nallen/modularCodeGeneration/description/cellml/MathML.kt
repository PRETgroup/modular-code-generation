package me.nallen.modularcodegeneration.description.cellml

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
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

        return when(operation) {
            Operation.EQ -> NAryOperation.create(id, operation, arguments)
            Operation.NEQ -> BinaryOperation.create(id, operation, arguments)
            Operation.GT -> NAryOperation.create(id, operation, arguments)
            Operation.LT -> NAryOperation.create(id, operation, arguments)
            Operation.GEQ -> NAryOperation.create(id, operation, arguments)
            Operation.LEQ -> NAryOperation.create(id, operation, arguments)
            Operation.PLUS -> NAryOperation.create(id, operation, arguments)
            Operation.MINUS -> Minus.create(id, arguments)
            Operation.TIMES -> NAryOperation.create(id, operation, arguments)
            Operation.DIVIDE -> BinaryOperation.create(id, operation, arguments)
            Operation.POWER -> BinaryOperation.create(id, operation, arguments)
            Operation.ROOT -> TODO("ROOT")
            Operation.ABS -> UnaryOperation.create(id, operation, arguments)
            Operation.EXP -> UnaryOperation.create(id, operation, arguments)
            Operation.LN -> UnaryOperation.create(id, operation, arguments)
            Operation.LOG -> TODO("LOG")
            Operation.FLOOR -> UnaryOperation.create(id, operation, arguments)
            Operation.CEILING -> UnaryOperation.create(id, operation, arguments)
            Operation.FACTORIAL -> UnaryOperation.create(id, operation, arguments)
            Operation.AND -> NAryOperation.create(id, operation, arguments)
            Operation.OR -> NAryOperation.create(id, operation, arguments)
            Operation.XOR -> NAryOperation.create(id, operation, arguments)
            Operation.NOT -> UnaryOperation.create(id, operation, arguments)
            Operation.DIFF -> Diff.create(id, arguments)
        }
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
    abstract fun generateString(variableUnits: Map<String, SimpleUnit> = mapOf()): String
    abstract fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>): SimpleUnit
}

sealed class Apply(
        open val id: String?,
        val operation: Operation
): MathItem() {
    override fun generateString(variableUnits: Map<String, SimpleUnit>): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>): SimpleUnit {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

data class Diff(
        override val id: String?,
        val bvar: Bvar,
        val argument: MathItem
): Apply(id, Operation.DIFF) {
    companion object Factory {
        fun create(id: String?, arguments: List<MathItem>): Apply {
            if(arguments.size != 2)
                throw Exception("Invalid number of arguments provided to <diff>")

            if(arguments[0] !is Bvar)
                throw Exception("Invalid argument provided to <diff>")

            return Diff(id, arguments[0] as Bvar, arguments[1])
        }
    }
}

data class Minus(
        override val id: String?,
        val argument1: MathItem,
        val argument2: MathItem?
): Apply(id, Operation.MINUS) {
    companion object Factory {
        fun create(id: String?, arguments: List<MathItem>): Apply {
            if(arguments.isEmpty() || arguments.size > 2)
                throw Exception("Invalid number of arguments provided to <minus>")

            return Minus(id, arguments[0], arguments.getOrNull(1))
        }
    }
}

data class UnaryOperation(
        override val id: String?,
        val operation2: Operation,
        val argument: MathItem
): Apply(id, operation2) {
    companion object Factory {
        fun create(id: String?, operation: Operation, arguments: List<MathItem>): UnaryOperation {
            if(arguments.size != 1)
                throw Exception("Invalid number of arguments provided to <${operation.getIdentifier()}>")

            return UnaryOperation(id, operation, arguments[0])
        }
    }
}

data class BinaryOperation(
        override val id: String?,
        val operation2: Operation,
        val left: MathItem,
        val right: MathItem
): Apply(id, operation2) {
    companion object Factory {
        fun create(id: String?, operation: Operation, arguments: List<MathItem>): BinaryOperation {
            if(arguments.size != 2)
                throw Exception("Invalid number of arguments provided to <${operation.getIdentifier()}>")

            return BinaryOperation(id, operation, arguments[0], arguments[1])
        }
    }
}

data class NAryOperation(
        override val id: String?,
        val operation2: Operation,
        val arguments: List<MathItem>
): Apply(id, operation2) {
    companion object Factory {
        fun create(id: String?, operation: Operation, arguments: List<MathItem>): NAryOperation {
            if(arguments.size < 0)
                throw Exception("Invalid number of arguments provided to <${operation.getIdentifier()}>")

            return NAryOperation(id, operation, arguments)
        }
    }
}



sealed class MathValue : MathItem()

data class Cn(
        val units: String,
        val value: Double
): MathValue() {
    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>): SimpleUnit {
        if(!unitsMap.containsKey(units))
            throw Exception("Unknown units provided: $units")

        return unitsMap[units]!!
    }

    override fun generateString(variableUnits: Map<String, SimpleUnit>): String {
        return value.toString()
    }
}

data class Ci(
        val name: String
): MathValue() {
    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>): SimpleUnit {
        TODO()
    }

    override fun generateString(variableUnits: Map<String, SimpleUnit>): String {
        TODO()
    }
}

data class Bvar(
        val variable: Ci,
        val degree: Degree? = null
): MathItem() {
    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>): SimpleUnit {
        TODO()
    }

    override fun generateString(variableUnits: Map<String, SimpleUnit>): String {
        TODO()
    }
}

data class Degree(
        val order: MathValue
): MathItem() {
    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>): SimpleUnit {
        val orderUnits = order.calculateUnits(variableUnits, unitsMap)

        if(orderUnits !is CompositeUnit || orderUnits.baseUnits.isNotEmpty())
            throw Exception("<degree> requires child to be dimensionless")

        return orderUnits
    }

    override fun generateString(variableUnits: Map<String, SimpleUnit>): String {
        return order.generateString(mapOf())
    }
}

enum class Operation {
    EQ, NEQ, GT, LT, GEQ, LEQ,
    PLUS, MINUS, TIMES, DIVIDE, POWER, ROOT, ABS, EXP, LN, LOG, FLOOR, CEILING, FACTORIAL,
    AND, OR, XOR, NOT,
    DIFF;
    /*SIN, COS, TAN, SEC, CSC, COT, SINH, COSH, TANH, SECH, CSCH, COTH,
    ARCSIN, ARCCOS, ARCTAN, ARCCOSH, ARCCOT, ARCCOTH, ARCCSC, ARCCSCH, ARCSEC, ARCSECH, ARCSINH, ARCTANH;*/

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

    fun getIdentifier(): String {
        return when(this) {
            Operation.EQ -> "eq"
            Operation.NEQ -> "neq"
            Operation.GT -> "gt"
            Operation.LT -> "lt"
            Operation.GEQ -> "geq"
            Operation.LEQ -> "leq"
            Operation.PLUS -> "plus"
            Operation.MINUS -> "minus"
            Operation.TIMES -> "times"
            Operation.DIVIDE -> "divide"
            Operation.POWER -> "power"
            Operation.ROOT -> "root"
            Operation.ABS -> "abs"
            Operation.EXP -> "exp"
            Operation.LN -> "ln"
            Operation.LOG -> "log"
            Operation.FLOOR -> "floor"
            Operation.CEILING -> "ceiling"
            Operation.FACTORIAL -> "factorial"
            Operation.AND -> "and"
            Operation.OR -> "or"
            Operation.XOR -> "xor"
            Operation.NOT -> "not"
            Operation.DIFF -> "diff"
        }
    }
}