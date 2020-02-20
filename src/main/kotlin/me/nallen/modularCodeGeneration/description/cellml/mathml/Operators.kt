package me.nallen.modularcodegeneration.description.cellml.mathml

import me.nallen.modularcodegeneration.description.cellml.BooleanUnit
import me.nallen.modularcodegeneration.description.cellml.CompositeUnit
import me.nallen.modularcodegeneration.description.cellml.SimpleUnit

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
            EQ -> "eq"
            NEQ -> "neq"
            GT -> "gt"
            LT -> "lt"
            GEQ -> "geq"
            LEQ -> "leq"
            PLUS -> "plus"
            MINUS -> "minus"
            TIMES -> "times"
            DIVIDE -> "divide"
            POWER -> "power"
            ROOT -> "root"
            ABS -> "abs"
            EXP -> "exp"
            LN -> "ln"
            LOG -> "log"
            FLOOR -> "floor"
            CEILING -> "ceiling"
            FACTORIAL -> "factorial"
            AND -> "and"
            OR -> "or"
            XOR -> "xor"
            NOT -> "not"
            DIFF -> "diff"
        }
    }
}

sealed class MathItem {
    abstract fun generateString(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double> = mapOf(), name: String? = null, withUnits: Boolean = true): String
    abstract fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double> = mapOf()): SimpleUnit
    abstract fun evaluate(variableValues: Map<String, Double> = mapOf()): Double

    fun generateOffsetString(base: MathItem, variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double> = mapOf(), name: String? = null, withUnits: Boolean = true): String {
        if(withUnits) {
            val (mult, offset) = base.calculateUnits(variableUnits, unitsMap, constantValues).getDifferenceTo(this.calculateUnits(variableUnits, unitsMap, constantValues))
                    ?: throw Exception("Unable to get mapping between arguments for <???>")

            if(mult != 1.0 && offset != 0.0) {
                return "(${this.generateString(variableUnits, unitsMap, variableMap, constantValues, name, withUnits)}) * $mult + $offset"
            }
            else if(mult != 1.0) {
                return "(${this.generateString(variableUnits, unitsMap, variableMap, constantValues, name, withUnits)}) * $mult"
            }
            else if(offset != 0.0) {
                return "(${this.generateString(variableUnits, unitsMap, variableMap, constantValues, name, withUnits)}) + $offset"
            }
        }

        return this.generateString(variableUnits, unitsMap, variableMap, constantValues, name, withUnits)
    }

    abstract fun extractAllVariables(): List<String>

    abstract fun extractAllPiecewise(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double> = mapOf(), name: String? = null): Map<String, Pair<String, List<String>>>
}

sealed class Apply(
        open val id: String?,
        open val operation: Operation
): MathItem() {
    override fun generateString(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?, withUnits: Boolean): String {
        TODO("generateString <${operation.getIdentifier()}>")
    }

    override fun evaluate(variableValues: Map<String, Double>): Double {
        TODO("evaluate <${operation.getIdentifier()}>")
    }

    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        TODO("calculateUnits <${operation.getIdentifier()}>")
    }

    override fun extractAllVariables(): List<String> {
        TODO("extractAllVariables <${operation.getIdentifier()}>")
    }

    override fun extractAllPiecewise(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?): Map<String, Pair<String, List<String>>> {
        TODO("extractAllPiecewise <${operation.getIdentifier()}>")
    }
}

sealed class UnaryOperation(
        override val id: String?,
        override val operation: Operation,
        open val argument: MathItem
): Apply(id, operation) {
    companion object Factory {
        inline fun <reified T : UnaryOperation> create(id: String?, operation: Operation, arguments: List<MathItem>): UnaryOperation {
            if(arguments.size != 1)
                throw Exception("Invalid number of arguments provided to <${operation.getIdentifier()}>")

            return T::class.java.getConstructor(String::class.java, MathItem::class.java).newInstance(id, arguments[0])
        }
    }

    override fun extractAllVariables(): List<String> {
        return argument.extractAllVariables()
    }

    override fun extractAllPiecewise(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?): Map<String, Pair<String, List<String>>> {
        return argument.extractAllPiecewise(variableUnits, unitsMap, variableMap, constantValues, name)
    }
}

sealed class BinaryOperation(
        override val id: String?,
        override val operation: Operation,
        open val left: MathItem,
        open val right: MathItem
): Apply(id, operation) {
    companion object Factory {
        inline fun <reified T : BinaryOperation> create(id: String?, operation: Operation, arguments: List<MathItem>): BinaryOperation {
            if(arguments.size != 2)
                throw Exception("Invalid number of arguments provided to <${operation.getIdentifier()}>")

            return T::class.java.getConstructor(String::class.java, MathItem::class.java, MathItem::class.java).newInstance(id, arguments[0], arguments[1])
        }
    }

    override fun extractAllVariables(): List<String> {
        val variables = ArrayList<String>()
        variables.addAll(left.extractAllVariables())
        variables.addAll(right.extractAllVariables())
        return variables.distinct()
    }

    override fun extractAllPiecewise(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?): Map<String, Pair<String, List<String>>> {
        val piecewises = HashMap<String, Pair<String, List<String>>>()
        piecewises.putAll(left.extractAllPiecewise(variableUnits, unitsMap, variableMap, constantValues, name))
        piecewises.putAll(right.extractAllPiecewise(variableUnits, unitsMap, variableMap, constantValues, name))
        return piecewises
    }
}

sealed class NAryOperation(
        override val id: String?,
        override val operation: Operation,
        open val arguments: List<MathItem>
): Apply(id, operation) {
    companion object Factory {
        inline fun <reified T : NAryOperation> create(id: String?, operation: Operation, arguments: List<MathItem>): NAryOperation {
            return T::class.java.getConstructor(String::class.java, List::class.java).newInstance(id, arguments)
        }
    }

    override fun extractAllVariables(): List<String> {
        val variables = ArrayList<String>()
        for(argument in arguments) {
            variables.addAll(argument.extractAllVariables())
        }
        return variables.distinct()
    }

    override fun extractAllPiecewise(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?): Map<String, Pair<String, List<String>>> {
        val piecewises = HashMap<String, Pair<String, List<String>>>()
        for(argument in arguments) {
            piecewises.putAll(argument.extractAllPiecewise(variableUnits, unitsMap, variableMap, constantValues, name))
        }
        return piecewises
    }
}

data class Eq(
        override val id: String?,
        override val arguments: List<MathItem>
): NAryOperation(id, Operation.EQ, arguments) {
    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        if(arguments.isNotEmpty()) {
            val units = arguments[0].calculateUnits(variableUnits, unitsMap, constantValues)

            for(i in 1 until arguments.size) {
                if(!units.canMapTo(arguments[i].calculateUnits(variableUnits, unitsMap, constantValues))) {
                    throw Exception("Arguments to <${operation.getIdentifier()}> are not of same units")
                }
            }
        }

        return BooleanUnit()
    }
}

data class Neq(
        override val id: String?,
        override val left: MathItem,
        override val right: MathItem
): BinaryOperation(id, Operation.NEQ, left, right) {
    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        val leftUnits = left.calculateUnits(variableUnits, unitsMap, constantValues)
        val rightUnits = right.calculateUnits(variableUnits, unitsMap, constantValues)

        if(!leftUnits.canMapTo(rightUnits))
            throw Exception("Arguments to <${operation.getIdentifier()}> are not of same units")

        return BooleanUnit()
    }
}

data class Gt(
        override val id: String?,
        override val arguments: List<MathItem>
): NAryOperation(id, Operation.GT, arguments) {
    override fun generateString(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?, withUnits: Boolean): String {
        if(arguments.isEmpty())
            return ""

        if(arguments.size != 2)
            throw Exception("Unable to generate string for <${operation.getIdentifier()}> with ${arguments.size} arguments")

        var str = "(${arguments[0].generateString(variableUnits, unitsMap, variableMap, constantValues, name, withUnits)})"
        for(i in 1 until arguments.size) {
            str += " > (${arguments[i].generateOffsetString(arguments[0], variableUnits, unitsMap, variableMap, constantValues, name, withUnits)})"
        }

        return str
    }
    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        if(arguments.isNotEmpty()) {
            val units = arguments[0].calculateUnits(variableUnits, unitsMap, constantValues)

            for(i in 1 until arguments.size) {
                if(!units.canMapTo(arguments[i].calculateUnits(variableUnits, unitsMap, constantValues))) {
                    throw Exception("Arguments to <${operation.getIdentifier()}> are not of same units")
                }
            }
        }

        return BooleanUnit()
    }
}

data class Lt(
        override val id: String?,
        override val arguments: List<MathItem>
): NAryOperation(id, Operation.LT, arguments) {
    override fun generateString(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?, withUnits: Boolean): String {
        if(arguments.isEmpty())
            return ""

        if(arguments.size != 2)
            throw Exception("Unable to generate string for <${operation.getIdentifier()}> with ${arguments.size} arguments")

        var str = "(${arguments[0].generateString(variableUnits, unitsMap, variableMap, constantValues, name, withUnits)})"
        for(i in 1 until arguments.size) {
            str += " < (${arguments[i].generateOffsetString(arguments[0], variableUnits, unitsMap, variableMap, constantValues, name, withUnits)})"
        }

        return str
    }

    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        if(arguments.isNotEmpty()) {
            val units = arguments[0].calculateUnits(variableUnits, unitsMap, constantValues)

            for(i in 1 until arguments.size) {
                if(!units.canMapTo(arguments[i].calculateUnits(variableUnits, unitsMap, constantValues))) {
                    throw Exception("Arguments to <${operation.getIdentifier()}> are not of same units")
                }
            }
        }

        return BooleanUnit()
    }
}

data class Geq(
        override val id: String?,
        override val arguments: List<MathItem>
): NAryOperation(id, Operation.GEQ, arguments) {
    override fun generateString(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?, withUnits: Boolean): String {
        if(arguments.isEmpty())
            return ""

        if(arguments.size != 2)
            throw Exception("Unable to generate string for <${operation.getIdentifier()}> with ${arguments.size} arguments")

        var str = "(${arguments[0].generateString(variableUnits, unitsMap, variableMap, constantValues, name, withUnits)})"
        for(i in 1 until arguments.size) {
            str += " >= (${arguments[i].generateOffsetString(arguments[0], variableUnits, unitsMap, variableMap, constantValues, name, withUnits)})"
        }

        return str
    }

    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        if(arguments.isNotEmpty()) {
            val units = arguments[0].calculateUnits(variableUnits, unitsMap, constantValues)

            for(i in 1 until arguments.size) {
                if(!units.canMapTo(arguments[i].calculateUnits(variableUnits, unitsMap, constantValues))) {
                    throw Exception("Arguments to <${operation.getIdentifier()}> are not of same units")
                }
            }
        }

        return BooleanUnit()
    }
}

data class Leq(
        override val id: String?,
        override val arguments: List<MathItem>
): NAryOperation(id, Operation.LEQ, arguments) {
    override fun generateString(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?, withUnits: Boolean): String {
        if(arguments.isEmpty())
            return ""

        if(arguments.size != 2)
            throw Exception("Unable to generate string for <${operation.getIdentifier()}> with ${arguments.size} arguments")

        var str = "(${arguments[0].generateString(variableUnits, unitsMap, variableMap, constantValues, name, withUnits)})"
        for(i in 1 until arguments.size) {
            str += " <= (${arguments[i].generateOffsetString(arguments[0], variableUnits, unitsMap, variableMap, constantValues, name, withUnits)})"
        }

        return str
    }

    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        if(arguments.isNotEmpty()) {
            val units = arguments[0].calculateUnits(variableUnits, unitsMap, constantValues)

            for(i in 1 until arguments.size) {
                if(!units.canMapTo(arguments[i].calculateUnits(variableUnits, unitsMap, constantValues))) {
                    throw Exception("Arguments to <${operation.getIdentifier()}> are not of same units")
                }
            }
        }

        return BooleanUnit()
    }
}

data class Plus(
        override val id: String?,
        override val arguments: List<MathItem>
): NAryOperation(id, Operation.PLUS, arguments) {
    override fun generateString(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?, withUnits: Boolean): String {
        if(arguments.isEmpty())
            return ""

        var str = "(${arguments[0].generateString(variableUnits, unitsMap, variableMap, constantValues, name, withUnits)})"
        for(i in 1 until arguments.size) {
            str += " + (${arguments[i].generateOffsetString(arguments[0], variableUnits, unitsMap, variableMap, constantValues, name, withUnits)})"
        }

        return str
    }

    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        if(arguments.isNotEmpty()) {
            val units = arguments[0].calculateUnits(variableUnits, unitsMap, constantValues)

            for(i in 1 until arguments.size) {
                if(!units.canMapTo(arguments[i].calculateUnits(variableUnits, unitsMap, constantValues))) {
                    throw Exception("Arguments to <${operation.getIdentifier()}> are not of same units")
                }
            }

            return units
        }

        return CompositeUnit()
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

    override fun generateString(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?, withUnits: Boolean): String {
        if(argument2 == null) {
            return argument1.generateString(variableUnits, unitsMap, variableMap, constantValues, name, withUnits)
        }

        return "(${argument1.generateString(variableUnits, unitsMap, variableMap, constantValues, name, withUnits)}) - (${argument2.generateOffsetString(argument1, variableUnits, unitsMap, variableMap, constantValues, name, withUnits)})"
    }

    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        val firstUnits = argument1.calculateUnits(variableUnits, unitsMap, constantValues)
        if(argument2 != null) {
            val secondUnits = argument2.calculateUnits(variableUnits, unitsMap, constantValues)

            if(!firstUnits.canMapTo(secondUnits)) {
                throw Exception("Arguments to <${operation.getIdentifier()}> are not of same units")
            }
        }

        return firstUnits
    }

    override fun extractAllVariables(): List<String> {
        val variables = ArrayList<String>()
        variables.addAll(argument1.extractAllVariables())
        if(argument2 != null)
            variables.addAll(argument2.extractAllVariables())
        return variables.distinct()
    }

    override fun extractAllPiecewise(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?): Map<String, Pair<String, List<String>>> {
        val piecewises = HashMap<String, Pair<String, List<String>>>()
        piecewises.putAll(argument1.extractAllPiecewise(variableUnits, unitsMap, variableMap, constantValues, name))
        if(argument2 != null)
            piecewises.putAll(argument2.extractAllPiecewise(variableUnits, unitsMap, variableMap, constantValues, name))
        return piecewises
    }
}

data class Times(
        override val id: String?,
        override val arguments: List<MathItem>
): NAryOperation(id, Operation.TIMES, arguments) {
    override fun generateString(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?, withUnits: Boolean): String {
        if(arguments.isEmpty())
            return ""

        var str = "(${arguments[0].generateString(variableUnits, unitsMap, variableMap, constantValues, name, withUnits)})"
        for(i in 1 until arguments.size) {
            str += " * (${arguments[i].generateString(variableUnits, unitsMap, variableMap, constantValues, name, withUnits)})"
        }

        return str
    }

    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        if(arguments.isNotEmpty()) {
            var units: SimpleUnit = CompositeUnit()

            for(argument in arguments) {
                units = units.createMultiplication(argument.calculateUnits(variableUnits, unitsMap, constantValues))
            }

            return units
        }

        return CompositeUnit()
    }
}

data class Divide(
        override val id: String?,
        override val left: MathItem,
        override val right: MathItem
): BinaryOperation(id, Operation.DIVIDE, left, right) {
    override fun generateString(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?, withUnits: Boolean): String {
        return "(${left.generateString(variableUnits, unitsMap, variableMap, constantValues, name, withUnits)}) / (${right.generateString(variableUnits, unitsMap, variableMap, constantValues, name, withUnits)})"
    }

    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        val leftUnits = left.calculateUnits(variableUnits, unitsMap, constantValues)
        val rightUnits = right.calculateUnits(variableUnits, unitsMap, constantValues)

        return leftUnits.createMultiplication(rightUnits.createToPowerOf(-1.0))
    }
}

data class Power(
        override val id: String?,
        override val left: MathItem,
        override val right: MathItem
): BinaryOperation(id, Operation.POWER, left, right) {
    override fun generateString(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?, withUnits: Boolean): String {
        return "pow(${left.generateString(variableUnits, unitsMap, variableMap, constantValues, name, withUnits)}, ${right.generateString(variableUnits, unitsMap, variableMap, constantValues, name, withUnits)})"
    }

    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        val leftUnits = left.calculateUnits(variableUnits, unitsMap, constantValues)
        val rightUnits = right.calculateUnits(variableUnits, unitsMap, constantValues)

        if(!CompositeUnit().canMapTo(rightUnits)) {
            throw Exception("Argument 2 of <${operation.getIdentifier()}> is expected to be dimensionless")
        }

        return leftUnits.createToPowerOf(right.evaluate(constantValues))
    }
}

data class Root(
        override val id: String?,
        val inner: MathItem,
        val degree: Degree? = null
): Apply(id, Operation.ROOT) {
    companion object Factory {
        fun create(id: String?, arguments: List<MathItem>): Root {
            if(arguments.isEmpty() || arguments.size > 2)
                throw Exception("Invalid number of arguments provided to <root>")

            return if(arguments.size == 1) {
                Root(id, arguments[0], null)
            } else {
                if(arguments[0] !is Degree)
                    throw Exception("Expected first argument to <root> to be of type <degree>")
                Root(id, arguments[1], arguments[0] as Degree)
            }
        }
    }

    override fun generateString(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?, withUnits: Boolean): String {
        if(degree != null && degree.evaluate(constantValues) != 2.0) {
            throw Exception("Only <${operation.getIdentifier()}> with a <degree> of 2.0 are currently supported")
        }

        return "sqrt(${inner.generateString(variableUnits, unitsMap, variableMap, constantValues, name, withUnits)})"
    }

    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        val innerUnits = inner.calculateUnits(variableUnits, unitsMap, constantValues)

        var power = 2.0
        if(degree != null) {
            val degreeUnits = degree.calculateUnits(variableUnits, unitsMap, constantValues)
            if(!CompositeUnit().canMapTo(degreeUnits)) {
                throw Exception("<degree> argument of <${operation.getIdentifier()}> is expected to be dimensionless")
            }

            power = degree.evaluate(constantValues)
        }

        return innerUnits.createToPowerOf(1 / power)
    }

    override fun extractAllVariables(): List<String> {
        val variables = ArrayList<String>()
        variables.addAll(inner.extractAllVariables())
        if(degree != null)
            variables.addAll(degree.extractAllVariables())
        return variables.distinct()
    }

    override fun extractAllPiecewise(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?): Map<String, Pair<String, List<String>>> {
        val piecewises = HashMap<String, Pair<String, List<String>>>()
        piecewises.putAll(inner.extractAllPiecewise(variableUnits, unitsMap, variableMap, constantValues, name))
        if(degree != null)
            piecewises.putAll(degree.extractAllPiecewise(variableUnits, unitsMap, variableMap, constantValues, name))
        return piecewises
    }
}

data class Abs(
        override val id: String?,
        override val argument: MathItem
): UnaryOperation(id, Operation.ABS, argument) {
    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        return argument.calculateUnits(variableUnits, unitsMap, constantValues)
    }
}

data class Exp(
        override val id: String?,
        override val argument: MathItem
): UnaryOperation(id, Operation.EXP, argument) {
    override fun generateString(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?, withUnits: Boolean): String {
        return "exp(${argument.generateString(variableUnits, unitsMap, variableMap, constantValues, name, withUnits)})"
    }

    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        val units = argument.calculateUnits(variableUnits, unitsMap, constantValues)

        if(!CompositeUnit().canMapTo(units)) {
            throw Exception("Argument of <${operation.getIdentifier()}> is expected to be dimensionless")
        }

        return CompositeUnit()
    }
}

data class Ln(
        override val id: String?,
        override val argument: MathItem
): UnaryOperation(id, Operation.LN, argument) {
    override fun generateString(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?, withUnits: Boolean): String {
        return "ln(${argument.generateString(variableUnits, unitsMap, variableMap, constantValues, name, withUnits)})"
    }

    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        val units = argument.calculateUnits(variableUnits, unitsMap, constantValues)

        if(!CompositeUnit().canMapTo(units)) {
            throw Exception("Argument of <${operation.getIdentifier()}> is expected to be dimensionless")
        }

        return CompositeUnit()
    }
}

data class Floor(
        override val id: String?,
        override val argument: MathItem
): UnaryOperation(id, Operation.FLOOR, argument) {
    override fun generateString(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?, withUnits: Boolean): String {
        return "floor(${argument.generateString(variableUnits, unitsMap, variableMap, constantValues, name, withUnits)})"
    }

    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        return argument.calculateUnits(variableUnits, unitsMap, constantValues)
    }
}

data class Ceiling(
        override val id: String?,
        override val argument: MathItem
): UnaryOperation(id, Operation.CEILING, argument) {
    override fun generateString(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?, withUnits: Boolean): String {
        return "ceil(${argument.generateString(variableUnits, unitsMap, variableMap, constantValues, name, withUnits)})"
    }

    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        return argument.calculateUnits(variableUnits, unitsMap, constantValues)
    }
}

data class Factorial(
        override val id: String?,
        override val argument: MathItem
): UnaryOperation(id, Operation.FACTORIAL, argument) {
    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        val units = argument.calculateUnits(variableUnits, unitsMap, constantValues)

        if(!CompositeUnit().canMapTo(units)) {
            throw Exception("Argument of <${operation.getIdentifier()}> is expected to be dimensionless")
        }

        return CompositeUnit()
    }
}

data class And(
        override val id: String?,
        override val arguments: List<MathItem>
): NAryOperation(id, Operation.AND, arguments) {
    override fun generateString(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?, withUnits: Boolean): String {
        if(arguments.isEmpty())
            return ""

        var str = "(${arguments[0].generateString(variableUnits, unitsMap, variableMap, constantValues, name, withUnits)})"
        for(i in 1 until arguments.size) {
            str += " && (${arguments[i].generateOffsetString(arguments[0], variableUnits, unitsMap, variableMap, constantValues, name, withUnits)})"
        }

        return str
    }

    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        for(argument in arguments) {
            val units = argument.calculateUnits(variableUnits, unitsMap, constantValues)

            if(units !is BooleanUnit) {
                throw Exception("Argument of <${operation.getIdentifier()}> is expected to be boolean")
            }
        }

        return BooleanUnit()
    }
}

data class Or(
        override val id: String?,
        override val arguments: List<MathItem>
): NAryOperation(id, Operation.OR, arguments) {
    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        for(argument in arguments) {
            val units = argument.calculateUnits(variableUnits, unitsMap, constantValues)

            if(units !is BooleanUnit) {
                throw Exception("Argument of <${operation.getIdentifier()}> is expected to be boolean")
            }
        }

        return BooleanUnit()
    }
}

data class Xor(
        override val id: String?,
        override val arguments: List<MathItem>
): NAryOperation(id, Operation.XOR, arguments) {
    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        for(argument in arguments) {
            val units = argument.calculateUnits(variableUnits, unitsMap, constantValues)

            if(units !is BooleanUnit) {
                throw Exception("Argument of <${operation.getIdentifier()}> is expected to be boolean")
            }
        }

        return BooleanUnit()
    }
}

data class Not(
        override val id: String?,
        override val argument: MathItem
): UnaryOperation(id, Operation.NOT, argument) {
    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        val units = argument.calculateUnits(variableUnits, unitsMap, constantValues)

        if(units !is BooleanUnit) {
            throw Exception("Argument of <${operation.getIdentifier()}> is expected to be boolean")
        }

        return BooleanUnit()
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

    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        val bvarUnits = bvar.calculateUnits(variableUnits, unitsMap, constantValues).createToPowerOf(-1.0) //unitsMap[variableUnits["time"]]!!.createToPowerOf(-1.0)//
        val argumentUnits = argument.calculateUnits(variableUnits, unitsMap, constantValues)

        return argumentUnits.createMultiplication(bvarUnits)
    }
}

sealed class MathValue : MathItem()

data class Cn(
        val units: String,
        val value: Double
): MathValue() {
    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        if(!unitsMap.containsKey(units))
            throw Exception("Unknown units provided: $units")

        return unitsMap[units]!!
    }

    override fun generateString(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?, withUnits: Boolean): String {
        return value.toString()
    }

    override fun evaluate(variableValues: Map<String, Double>): Double {
        return value
    }

    override fun extractAllVariables(): List<String> {
        return listOf()
    }

    override fun extractAllPiecewise(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?): Map<String, Pair<String, List<String>>> {
        return mapOf()
    }
}

data class Ci(
        val name: String
): MathValue() {
    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        if(!variableUnits.containsKey(name)) {
            throw Exception("Unknown variable '$name' used in equation.")
        }

        if(!unitsMap.containsKey(variableUnits[name])) {
            throw Exception("Unknown units '${variableUnits[name]}' used")
        }

        return unitsMap[variableUnits[name]]!!
    }

    override fun generateString(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?, withUnits: Boolean): String {
        return variableMap[this.name]?:this.name
    }

    override fun evaluate(variableValues: Map<String, Double>): Double {
        if(!variableValues.containsKey(name)) {
            throw Exception("Unknown variable '$name' used in equation")
        }

        return variableValues[name]!!
    }

    override fun extractAllVariables(): List<String> {
        return listOf(name)
    }

    override fun extractAllPiecewise(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?): Map<String, Pair<String, List<String>>> {
        return mapOf()
    }
}

data class Bvar(
        val variable: Ci,
        val degree: Degree? = null
): MathItem() {
    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        if(degree != null) {
            return variable.calculateUnits(variableUnits, unitsMap, constantValues).createToPowerOf(degree.evaluate(constantValues))
        }

        return variable.calculateUnits(variableUnits, unitsMap, constantValues)
    }

    override fun generateString(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?, withUnits: Boolean): String {
        TODO("generateString <bvar>")
    }

    override fun evaluate(variableValues: Map<String, Double>): Double {
        TODO("evaluate <bvar>")
    }

    override fun extractAllVariables(): List<String> {
        TODO("extractAllVariables <bvar>")
    }

    override fun extractAllPiecewise(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?): Map<String, Pair<String, List<String>>> {
        TODO("extractAllPiecewise <bvar>")
    }
}

data class Degree(
        val order: MathValue
): MathItem() {
    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        val orderUnits = order.calculateUnits(variableUnits, unitsMap, constantValues)

        if(!CompositeUnit().canMapTo(orderUnits))
            throw Exception("<degree> required to be dimensionless")

        return CompositeUnit()
    }

    override fun generateString(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?, withUnits: Boolean): String {
        return order.generateString(variableUnits, unitsMap, variableMap, constantValues, name, withUnits)
    }

    override fun evaluate(variableValues: Map<String, Double>): Double {
        return order.evaluate(variableValues)
    }

    override fun extractAllVariables(): List<String> {
        TODO("extractAllVariables <degree>")
    }

    override fun extractAllPiecewise(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?): Map<String, Pair<String, List<String>>> {
        TODO("extractAllPiecewise <degree>")
    }
}

data class Piecewise(
        val pieces: List<Piece>,
        val otherwise: MathItem?
): MathItem() {
    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        if(pieces.isNotEmpty()) {
            val units = pieces[0].calculateUnits(variableUnits, unitsMap, constantValues)

            for(i in 1 until pieces.size) {
                if(!units.canMapTo(pieces[i].calculateUnits(variableUnits, unitsMap, constantValues))) {
                    throw Exception("Arguments to <piecewise> are not of same units")
                }
            }

            if(otherwise != null) {
                if(!units.canMapTo(otherwise.calculateUnits(variableUnits, unitsMap, constantValues))) {
                    throw Exception("Arguments to <piecewise> are not of same units")
                }
            }

            return units
        }

        return CompositeUnit()
    }

    override fun generateString(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?, withUnits: Boolean): String {
        return "${name}_pw(${this.extractAllVariables().joinToString(", ")})"
    }

    override fun evaluate(variableValues: Map<String, Double>): Double {
        TODO("evaluate <piecewise>")
    }

    override fun extractAllVariables(): List<String> {
        val variables = ArrayList<String>()
        for(piece in pieces) {
            variables.addAll(piece.extractAllVariables())
        }
        if(otherwise != null) {
            variables.addAll(otherwise.extractAllVariables())
        }

        return variables.distinct()
    }

    override fun extractAllPiecewise(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?): Map<String, Pair<String, List<String>>> {
        //TODO: Include support for nested piecewise?

        var functionString = ""

        var atLeastOneIf = false
        for(piece in pieces) {
            if(atLeastOneIf) {
                functionString += "else "
            }
            functionString += "if(${piece.condition.generateString(variableUnits, unitsMap, variableMap, constantValues, name, true)}) {\n"
            functionString += "  return ${piece.value.generateString(variableUnits, unitsMap, variableMap, constantValues, name, true)}\n"
            functionString += "}\n"

            atLeastOneIf = true
        }
        if(otherwise != null) {
            if(atLeastOneIf) {
                functionString += "else {\n"
            }
            functionString += "  return ${otherwise.generateString(variableUnits, unitsMap, variableMap, constantValues, name, true)}\n"
            if(atLeastOneIf) {
                functionString += "}\n"
            }
        }

        return mapOf(
                "${name}_pw" to Pair(functionString, this.extractAllVariables())
        )
    }
}

data class Piece(
        val value: MathItem,
        val condition: MathItem
): MathItem() {
    override fun calculateUnits(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, constantValues: Map<String, Double>): SimpleUnit {
        val conditionUnits = condition.calculateUnits(variableUnits, unitsMap, constantValues)

        if(conditionUnits !is BooleanUnit) {
            throw Exception("Condition argument of <piece> is expected to be boolean")
        }

        return value.calculateUnits(variableUnits, unitsMap, constantValues)
    }

    override fun generateString(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?, withUnits: Boolean): String {
        TODO("generateString <piece>")
    }

    override fun evaluate(variableValues: Map<String, Double>): Double {
        TODO("evaluate <piece>")
    }

    override fun extractAllVariables(): List<String> {
        val variables = ArrayList<String>()
        variables.addAll(value.extractAllVariables())
        variables.addAll(condition.extractAllVariables())
        return variables.distinct()
    }

    override fun extractAllPiecewise(variableUnits: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: Map<String, String>, constantValues: Map<String, Double>, name: String?): Map<String, Pair<String, List<String>>> {
        val piecewises = HashMap<String, Pair<String, List<String>>>()
        piecewises.putAll(value.extractAllPiecewise(variableUnits, unitsMap, variableMap, constantValues, name))
        piecewises.putAll(condition.extractAllPiecewise(variableUnits, unitsMap, variableMap, constantValues, name))
        return piecewises
    }
}