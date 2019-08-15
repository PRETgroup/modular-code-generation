package me.nallen.modularcodegeneration.description.cellml

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import me.nallen.modularcodegeneration.description.cellml.mathml.*
import me.nallen.modularcodegeneration.logging.Logger
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

        println(operation.getIdentifier())
        
        return when(operation) {
            Operation.EQ -> NAryOperation.create<Eq>(id, operation, arguments)
            Operation.NEQ -> BinaryOperation.create<Neq>(id, operation, arguments)
            Operation.GT -> NAryOperation.create<Gt>(id, operation, arguments)
            Operation.LT -> NAryOperation.create<Lt>(id, operation, arguments)
            Operation.GEQ -> NAryOperation.create<Geq>(id, operation, arguments)
            Operation.LEQ -> NAryOperation.create<Leq>(id, operation, arguments)
            Operation.PLUS -> NAryOperation.create<Plus>(id, operation, arguments)
            Operation.MINUS -> Minus.create(id, arguments)
            Operation.TIMES -> NAryOperation.create<Times>(id, operation, arguments)
            Operation.DIVIDE -> BinaryOperation.create<Divide>(id, operation, arguments)
            Operation.POWER -> BinaryOperation.create<Power>(id, operation, arguments)
            Operation.ROOT -> TODO("ROOT")
            Operation.ABS -> UnaryOperation.create<Abs>(id, operation, arguments)
            Operation.EXP -> UnaryOperation.create<Exp>(id, operation, arguments)
            Operation.LN -> UnaryOperation.create<Ln>(id, operation, arguments)
            Operation.LOG -> TODO("LOG")
            Operation.FLOOR -> UnaryOperation.create<Floor>(id, operation, arguments)
            Operation.CEILING -> UnaryOperation.create<Ceiling>(id, operation, arguments)
            Operation.FACTORIAL -> UnaryOperation.create<Factorial>(id, operation, arguments)
            Operation.AND -> NAryOperation.create<And>(id, operation, arguments)
            Operation.OR -> NAryOperation.create<Or>(id, operation, arguments)
            Operation.XOR -> NAryOperation.create<Xor>(id, operation, arguments)
            Operation.NOT -> UnaryOperation.create<Not>(id, operation, arguments)
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


