package me.nallen.modularcodegeneration.description.cellml

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import me.nallen.modularcodegeneration.codegen.Configuration
import me.nallen.modularcodegeneration.description.cellml.mathml.*
import me.nallen.modularcodegeneration.hybridautomata.*
import me.nallen.modularcodegeneration.logging.Logger
import me.nallen.modularcodegeneration.parsetree.ParseTreeItem
import me.nallen.modularcodegeneration.parsetree.Program
import me.nallen.modularcodegeneration.parsetree.VariableDeclaration
import me.nallen.modularcodegeneration.parsetree.VariableType
import java.io.File
import java.lang.Math
import java.util.*
import kotlin.collections.HashMap

typealias HybridVariable = me.nallen.modularcodegeneration.hybridautomata.Variable

/**
 * An Importer which is capable of reading in a CellML Document specification and creating the associated Hybrid Item
 * as described in the document.
 */
class Importer {
    companion object Factory {
        /**
         * Imports the CellML document at the specified path and converts it to a Hybrid Item.
         */
        fun import(path: String): Pair<HybridItem, Configuration> {
            val file = File(path)

            // Try to open the file
            if(!file.exists() || !file.isFile)
                throw Exception("Whoops")

            val xmlMapper = XmlMapper()
            xmlMapper.configure(MapperFeature.INFER_CREATOR_FROM_CONSTRUCTOR_PROPERTIES,false);
            val cellMLTree: Model? = xmlMapper.registerModule(KotlinModule()).readValue(file, Model::class.java)

            // Check if we could actually import it as an XML file
            if(cellMLTree == null) {
                throw Exception("Invalid CellML file provided")
            }

            val network = createHybridNetwork(cellMLTree, standardUnitsMap)

            return Pair(network, Configuration())
        }
    }
}

private val standardUnitsMap = mapOf(
        "ampere" to BaseUnit("ampere"),
        "candela" to BaseUnit("candela"),
        "kelvin" to BaseUnit("kelvin"),
        "kilogram" to BaseUnit("kilogram"),
        "meter" to BaseUnit("meter"),
        "mole" to BaseUnit("mole"),
        "second" to BaseUnit("second"),

        "dimensionless" to CompositeUnit(),

        "becquerel" to CompositeUnit(listOf(
              BaseUnitInstance(BaseUnit("second"), -1.0))),
        "celsius" to CompositeUnit(offset = 273.15, baseUnits = listOf(
                BaseUnitInstance(BaseUnit("kelvin")))),
        "coulomb" to CompositeUnit(listOf(
                BaseUnitInstance(BaseUnit("second")),
                BaseUnitInstance(BaseUnit("ampere")))),
        "farad" to CompositeUnit(listOf(
                BaseUnitInstance(BaseUnit("kilogram"), -1.0),
                BaseUnitInstance(BaseUnit("meter"), -2.0),
                BaseUnitInstance(BaseUnit("second"), 4.0),
                BaseUnitInstance(BaseUnit("ampere"), 2.0))),
        "gram" to CompositeUnit(multiply = 1E-3, baseUnits = listOf(
                BaseUnitInstance(BaseUnit("kilogram")))),
        "gray" to CompositeUnit(listOf(
                BaseUnitInstance(BaseUnit("meter"), 2.0),
                BaseUnitInstance(BaseUnit("second"), -2.0))),
        "henry" to CompositeUnit(listOf(
                BaseUnitInstance(BaseUnit("kilogram")),
                BaseUnitInstance(BaseUnit("meter"), 2.0),
                BaseUnitInstance(BaseUnit("second"), -2.0),
                BaseUnitInstance(BaseUnit("ampere"), -2.0))),
        "hertz" to CompositeUnit(listOf(
                BaseUnitInstance(BaseUnit("second"), -1.0))),
        "joule" to CompositeUnit(listOf(
                BaseUnitInstance(BaseUnit("kilogram")),
                BaseUnitInstance(BaseUnit("meter"), 2.0),
                BaseUnitInstance(BaseUnit("second"), -2.0))),
        "katal" to CompositeUnit(listOf(
                BaseUnitInstance(BaseUnit("second"), -1.0),
                BaseUnitInstance(BaseUnit("mole")))),
        "litre" to CompositeUnit(multiply = 1E-3, baseUnits = listOf(
                BaseUnitInstance(BaseUnit("meter"), 3.0))),
        "liter" to CompositeUnit(multiply = 1E-3, baseUnits = listOf(
                BaseUnitInstance(BaseUnit("meter"), 3.0))),
        "lumen" to BaseUnit("candela"),
        "lux" to CompositeUnit(listOf(
                BaseUnitInstance(BaseUnit("meter"), -2.0),
                BaseUnitInstance(BaseUnit("candela")))),
        "metre" to BaseUnit("meter"),
        "newton" to CompositeUnit(listOf(
                BaseUnitInstance(BaseUnit("kilogram")),
                BaseUnitInstance(BaseUnit("meter")),
                BaseUnitInstance(BaseUnit("second"), -2.0))),
        "ohm" to CompositeUnit(listOf(
                BaseUnitInstance(BaseUnit("kilogram")),
                BaseUnitInstance(BaseUnit("meter"), 2.0),
                BaseUnitInstance(BaseUnit("second"), -3.0),
                BaseUnitInstance(BaseUnit("ampere"), -2.0))),
        "pascal" to CompositeUnit(listOf(
                BaseUnitInstance(BaseUnit("kilogram")),
                BaseUnitInstance(BaseUnit("meter"), -1.0),
                BaseUnitInstance(BaseUnit("second"), -2.0))),
        "radian" to CompositeUnit(),
        "siemens" to CompositeUnit(listOf(
                BaseUnitInstance(BaseUnit("kilogram"), -1.0),
                BaseUnitInstance(BaseUnit("meter"), -2.0),
                BaseUnitInstance(BaseUnit("second"), 3.0),
                BaseUnitInstance(BaseUnit("ampere"), 2.0))),
        "sievert" to CompositeUnit(listOf(
                BaseUnitInstance(BaseUnit("meter"), 2.0),
                BaseUnitInstance(BaseUnit("second"), -2.0))),
        "steradian" to CompositeUnit(),
        "tesla" to CompositeUnit(listOf(
                BaseUnitInstance(BaseUnit("kilogram")),
                BaseUnitInstance(BaseUnit("second"), -2.0),
                BaseUnitInstance(BaseUnit("ampere")))),
        "volt" to CompositeUnit(listOf(
                BaseUnitInstance(BaseUnit("kilogram")),
                BaseUnitInstance(BaseUnit("meter"), 2.0),
                BaseUnitInstance(BaseUnit("second"), -3.0),
                BaseUnitInstance(BaseUnit("ampere"), -1.0))),
        "watt" to CompositeUnit(listOf(
                BaseUnitInstance(BaseUnit("kilogram")),
                BaseUnitInstance(BaseUnit("meter"), 2.0),
                BaseUnitInstance(BaseUnit("second"), -3.0))),
        "weber" to CompositeUnit(listOf(
                BaseUnitInstance(BaseUnit("kilogram")),
                BaseUnitInstance(BaseUnit("meter"), 2.0),
                BaseUnitInstance(BaseUnit("second"), -2.0),
                BaseUnitInstance(BaseUnit("ampere"), -1.0)))
)

private fun createHybridNetwork(model: Model, existingUnitsMap: Map<String, SimpleUnit> = mapOf()): HybridNetwork {
    val network = HybridNetwork()

    network.name = model.name

    val unitsMap = extractSimpleUnits(model.units, existingUnitsMap)

    if(model.components != null)
        network.importComponents(model.components, unitsMap)

    if(model.connections != null)
        network.importConnections(model.connections)

    return network
}

private fun extractSimpleUnits(units: List<Units>?, existingUnitsMap: Map<String, SimpleUnit> = mapOf()): Map<String, SimpleUnit> {
    val unitsMap = HashMap(existingUnitsMap)
    if(units != null) {
        for(unit in units) {
            if(unit.baseUnits == "yes") {
                unitsMap.put(unit.name, BaseUnit(unit.name))
            }
            else {
                if(unit.subunits != null) {
                    val unitList = ArrayList<BaseUnitInstance>()
                    var multiply = 1.0
                    var offset = 0.0
                    for(subunit in unit.subunits) {
                        val baseUnits = subunit.getBaseUnits(unitsMap)

                        if(baseUnits is BaseUnit) {
                            unitList.add(BaseUnitInstance(baseUnits, subunit.exponent))
                        }
                        else if(baseUnits is CompositeUnit) {
                            for((baseUnit, exponent) in baseUnits.baseUnits) {
                                if(unitList.any { it.baseUnit.name == baseUnit.name }) {
                                    unitList.first { it.baseUnit.name == baseUnit.name }.exponent += exponent * subunit.exponent
                                }
                                else {
                                    unitList.add(BaseUnitInstance(baseUnit, exponent * subunit.exponent))
                                }
                            }

                            multiply *= Math.pow(baseUnits.multiply, subunit.exponent)
                        }

                        multiply *= subunit.multiplier * Math.pow(Math.pow(10.0, subunit.prefix.toDouble()), subunit.exponent)
                    }
                    unitsMap.put(unit.name, CompositeUnit(unitList, multiply, offset))
                }
            }
        }
    }

    return unitsMap
}

private fun Unit.getBaseUnits(baseUnits: Map<String, SimpleUnit>): SimpleUnit {
    if(!baseUnits.containsKey(this.units))
        throw Exception("Unknown units ${this.units}")

    return baseUnits[this.units]!!
}

private fun HybridNetwork.importComponents(components: List<Component>, existingUnitsMap: Map<String, SimpleUnit> = mapOf()) {
    for(component in components) {
        val definitionId = UUID.randomUUID()
        val instantiateId = UUID.randomUUID()
        this.definitions.put(definitionId, createHybridAutomata(component, existingUnitsMap))
        this.instances.put(component.name, AutomataInstance(instantiateId))
        this.instantiates.put(instantiateId, AutomataInstantiate(definitionId, component.name))
    }
}

private fun createHybridAutomata(component: Component, existingUnitsMap: Map<String, SimpleUnit> = mapOf()): HybridAutomata {
    val item = HybridAutomata()

    item.name = component.name

    val unitsMap = extractSimpleUnits(component.units, existingUnitsMap)

    val location = Location("q0")

    val variablesMap = HashMap<String, String>()

    if(component.variables != null) {
        for(variable in component.variables) {
            if(!unitsMap.containsKey(variable.units))
                throw Exception("Unknown units provided: ${variable.units}")

            variablesMap.put(variable.name, variable.units)
        }

        item.parseVariables(component.variables)
    }

    if(component.maths != null) {
        for(math in component.maths) {
            for(mathItem in math.items) {
                val result = parseMathEquation(mathItem, variablesMap, unitsMap)

                if(result != null) {
                    if(result.isFlow)
                        location.flow.put(result.variable, result.equation)
                    else
                        location.update.put(result.variable, result.equation)

                    for((name, program) in result.functions) {
                        val inputs = ArrayList<VariableDeclaration>()
                        for(input in program.second) {
                            inputs.add(VariableDeclaration(input, VariableType.REAL, me.nallen.modularcodegeneration.parsetree.Locality.EXTERNAL_INPUT))
                        }

                        program.first.collectVariables(inputs)

                        val functionDef = FunctionDefinition(name, program.first, inputs)
                        functionDef.returnType = program.first.getReturnType()
                        item.functions.add(functionDef)
                    }
                }
            }
        }
    }

    if(item.variables.any { it.name == "time" && (it.locality == Locality.INTERNAL || it.locality == Locality.EXTERNAL_OUTPUT) }) {
        val variable = item.variables.first { it.name == "time" && (it.locality == Locality.INTERNAL || it.locality == Locality.EXTERNAL_OUTPUT) }
        if(!location.flow.containsKey(variable.name) && !location.update.containsKey(variable.name)) {
            location.flow.put(variable.name, ParseTreeItem.generate(1))

            val existingUnit = component.variables?.firstOrNull { it.name == variable.name }?.units
            if(existingUnit != null) {
                val unit = Units(
                        name="time_rate",
                        subunits=listOf(
                                Unit(
                                        units=existingUnit
                                ),
                                Unit(
                                        units="second",
                                        exponent=-1.0
                                )
                        )
                )

                val timeMap = extractSimpleUnits(listOf(unit), unitsMap)

                if(timeMap.containsKey("time_rate")) {
                    val timeUnit = timeMap["time_rate"]
                    if(timeUnit is CompositeUnit) {
                        location.flow.put(variable.name, ParseTreeItem.generate(1 / timeUnit.multiply))
                    }
                }
            }
        }
    }

    item.init.state = "q0"

    item.locations.add(location)

    return item
}

data class MathParse(
        val variable: String,
        val equation: ParseTreeItem,
        val isFlow: Boolean,
        val functions: Map<String, Pair<Program, List<String>>> = mapOf()
)

private fun parseMathEquation(item: MathItem, variablesMap: Map<String, String>, unitsMap: Map<String, SimpleUnit>): MathParse? {
    if(item !is Apply)
        return null

    if(item.operation != Operation.EQ || item !is NAryOperation)
        return null

    if(item.arguments.size != 2)
        return null

    val arg0 = item.arguments[0]

    if(!arg0.calculateUnits(variablesMap, unitsMap).canMapTo(item.arguments[1].calculateUnits(variablesMap, unitsMap))) {
        throw Exception("Unable to map units")
    }

    if(arg0 is Ci) {
        val functions = HashMap<String, Pair<Program, List<String>>>()
        for((name, piecewise) in item.arguments[1].extractAllPiecewise(variablesMap, unitsMap, name =arg0.name)) {
            functions.put(name, Pair(Program.generate(piecewise.first), piecewise.second))
        }

        return MathParse(arg0.name, ParseTreeItem.generate(item.arguments[1].generateOffsetString(arg0, variablesMap, unitsMap, name=arg0.name)), false, functions)
    }

    if(arg0 is Diff) {
        try {
            if (arg0.bvar.variable.name == "time" && (arg0.bvar.degree == null || arg0.bvar.degree.evaluate() == 1.0)) {
                if(arg0.argument is Ci) {
                    val functions = HashMap<String, Pair<Program, List<String>>>()
                    for((name, piecewise) in item.arguments[1].extractAllPiecewise(variablesMap, unitsMap, name =arg0.argument.name)) {
                        functions.put(name, Pair(Program.generate(piecewise.first), piecewise.second))
                    }

                    return MathParse(arg0.argument.name, ParseTreeItem.generate(item.arguments[1].generateOffsetString(arg0, variablesMap, unitsMap, name=arg0.argument.name)), true, functions)
                }

            }
        }
        catch(e: Exception) {}

        throw Exception("Only able to map flow constraints which are with respect to first order time")
    }


    return null
}

private fun HybridItem.parseVariables(variables: List<Variable>) {
    for(variable in variables) {
        val name = variable.name

        val locality = when(variable.publicInterface) {
            InterfaceType.IN -> Locality.EXTERNAL_INPUT
            InterfaceType.OUT -> Locality.EXTERNAL_OUTPUT
            InterfaceType.NONE -> Locality.INTERNAL
        }

        this.variables.add(HybridVariable(name, VariableType.REAL, locality))

        if(this is HybridAutomata)
            if(locality == Locality.EXTERNAL_OUTPUT || locality == Locality.INTERNAL)
                this.init.valuations[name] = ParseTreeItem.generate(variable.initialValue)
    }
}

private fun HybridNetwork.importConnections(connections: List<Connection>) {
    for(connection in connections) {
        for(mapping in connection.variables) {
            val instance1 = this.instances[connection.components.component1]
            val instance2 = this.instances[connection.components.component2]

            if(instance1 != null && instance2 != null) {
                val instantiate1 = this.instantiates[instance1.instantiate]
                val instantiate2 = this.instantiates[instance2.instantiate]

                if(instantiate1 != null && instantiate2 != null) {
                    val definition1 = this.definitions[instantiate1.definition]
                    val definition2 = this.definitions[instantiate2.definition]

                    if(definition1 != null && definition2 != null) {
                        val variable1 = definition1.variables.find { it.name == mapping.variable1 }
                        val variable2 = definition2.variables.find { it.name == mapping.variable2 }

                        if(variable1 != null && variable2 != null) {
                            if(variable1.locality == Locality.EXTERNAL_OUTPUT && variable2.locality == Locality.EXTERNAL_INPUT) {
                                val to = AutomataVariablePair(connection.components.component2, mapping.variable2)
                                val from = connection.components.component1 + "." + mapping.variable1
                                this.ioMapping[to] = ParseTreeItem.generate(from)
                            }
                            else if(variable1.locality == Locality.EXTERNAL_INPUT && variable2.locality == Locality.EXTERNAL_OUTPUT) {
                                val to = AutomataVariablePair(connection.components.component1, mapping.variable1)
                                val from = connection.components.component2 + "." + mapping.variable2
                                this.ioMapping[to] = ParseTreeItem.generate(from)
                            }
                            else {
                                Logger.error("Unable to map variables '${connection.components.component1}.${mapping.variable1}' to '${connection.components.component2}.${mapping.variable2}'")
                            }
                        }
                        else {
                            if(variable1 == null)
                                Logger.error("Unable to find variable ${mapping.variable1} in ${connection.components.component1}")

                            if(variable2 == null)
                                Logger.error("Unable to find variable ${mapping.variable2} in ${connection.components.component2}")
                        }
                    }
                }
            }
            else {
                if(instance1 == null)
                    Logger.error("Unable to find instance ${connection.components.component1}")

                if(instance2 == null)
                    Logger.error("Unable to find instance ${connection.components.component2}")
            }
        }
    }

    val sortedMappings = this.ioMapping.toList().sortedWith(compareBy({ it.first.automata }, { it.first.variable })).toMap()
    this.ioMapping.clear()
    this.ioMapping.putAll(sortedMappings)
}
