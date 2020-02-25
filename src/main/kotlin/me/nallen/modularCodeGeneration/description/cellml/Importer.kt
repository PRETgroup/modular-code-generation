package me.nallen.modularcodegeneration.description.cellml

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import me.nallen.modularcodegeneration.codegen.Configuration
import me.nallen.modularcodegeneration.description.Importer
import me.nallen.modularcodegeneration.description.cellml.Importer.Factory.variableMap
import me.nallen.modularcodegeneration.description.cellml.mathml.*
import me.nallen.modularcodegeneration.hybridautomata.*
import me.nallen.modularcodegeneration.logging.Logger
import me.nallen.modularcodegeneration.parsetree.ParseTreeItem
import me.nallen.modularcodegeneration.parsetree.Program
import me.nallen.modularcodegeneration.parsetree.VariableDeclaration
import me.nallen.modularcodegeneration.parsetree.VariableType
import me.nallen.modularcodegeneration.utils.getRelativePath
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
            val (contents, isUrl) = Importer.loadFromPath(path)

            if(isUrl) {
                val file = File(path).absoluteFile
                Logger.info("Reading source file ${file.getRelativePath()}")
            }
            else {
                Logger.info("Reading remote file $path")
            }

            val xmlMapper = XmlMapper().registerModule(KotlinModule())
            xmlMapper.configure(MapperFeature.INFER_CREATOR_FROM_CONSTRUCTOR_PROPERTIES, false)
            val cellMLTree: Model? = xmlMapper.readValue(contents, Model::class.java)

            // Check if we could actually import it as an XML file
            if(cellMLTree == null) {
                throw Exception("Invalid CellML file provided")
            }

            variableMap = HashMap()
            val network = createHybridNetwork(cellMLTree, standardUnitsMap)

            return Pair(network, Configuration())
        }

        var variableMap = HashMap<String, HashMap<String, String>>()
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

    val components = if(model.components != null)
        network.importComponents(model.components, unitsMap)
    else
        listOf()

    val encapsulationData = parseGroups(model.groups, components)

    if(model.connections != null)
        network.importConnections(model.connections, model.components?:listOf(), encapsulationData)

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
                    for(subunit in unit.subunits!!) {
                        val baseUnits = subunit.getBaseUnits(unitsMap)

                        if(baseUnits is BaseUnit) {
                            if(unitList.any { it.baseUnit.name == baseUnits.name }) {
                                unitList.first { it.baseUnit.name == baseUnits.name }.exponent += subunit.exponent
                            }
                            else {
                                unitList.add(BaseUnitInstance(baseUnits, subunit.exponent))
                            }
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

private fun HybridNetwork.importComponents(components: List<Component>, existingUnitsMap: Map<String, SimpleUnit> = mapOf()): List<String> {
    val componentNames = arrayListOf<String>()
    for(component in components) {
        val definitionId = UUID.randomUUID()
        val instantiateId = UUID.randomUUID()
        val definition = createHybridAutomata(component, existingUnitsMap);
        this.definitions.put(definitionId, definition)
        this.instances.put(component.name, AutomataInstance(instantiateId))
        this.instantiates.put(instantiateId, AutomataInstantiate(definitionId, component.name))
        componentNames.add(component.name)

        for(output in definition.variables.filter { it.locality == Locality.EXTERNAL_OUTPUT }) {
            val outputName = "${component.name}_${output.name}"
            this.variables.add(HybridVariable(outputName, output.type, output.locality))
            this.ioMapping.put(AutomataVariablePair("", outputName), ParseTreeItem.Factory.generate("${component.name}.${output.name}"))
        }
    }

    return componentNames
}

private fun createHybridAutomata(component: Component, existingUnitsMap: Map<String, SimpleUnit> = mapOf()): HybridAutomata {
    val item = HybridAutomata()

    item.name = component.name

    variableMap[item.name] = HashMap()
    val privateVariables = ArrayList<String>()

    val unitsMap = extractSimpleUnits(component.units, existingUnitsMap)

    val location = Location("q0")

    val variablesMap = HashMap<String, String>()

    if(component.variables != null) {
        for(variable in component.variables) {
            if(!unitsMap.containsKey(variable.units))
                throw Exception("Unknown units provided: ${variable.units}")

            variablesMap.put(variable.name, variable.units)
        }

        privateVariables.addAll(item.parseVariables(component.variables))
    }

    if(component.maths != null) {
        for(math in component.maths) {
            for(mathItem in math.items) {
                val result = parseMathEquation(mathItem, variablesMap, unitsMap, variableMap[item.name]!!)

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
                val unit = Units("time_rate")
                unit.subunits=listOf(
                        Unit(
                                units=existingUnit
                        ),
                        Unit(
                                units="second",
                                exponent=-1.0
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

    for(variable in privateVariables) {
        val itemVariable = item.variables.firstOrNull { it.name == "${variable}_private" }
        if(itemVariable != null) {
            if(itemVariable.locality == Locality.EXTERNAL_INPUT) {
                location.update[variable] = ParseTreeItem.generate("${variable}_private")
            }
            else if(itemVariable.locality == Locality.EXTERNAL_OUTPUT) {
                location.update["${variable}_private"] = ParseTreeItem.generate(variable)
            }
        }
    }

    for(variable in item.variables.filter { it.locality == Locality.INTERNAL }) {
        if(!location.update.containsKey(variable.name) && !location.flow.containsKey(variable.name)) {
            variable.locality = Locality.PARAMETER
            variable.defaultValue = item.init.valuations[variable.name]
            item.init.valuations.remove(variable.name)
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

private fun parseMathEquation(item: MathItem, variablesMap: Map<String, String>, unitsMap: Map<String, SimpleUnit>, variableMap: HashMap<String, String>): MathParse? {
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
        val variable = variableMap[arg0.name]?:arg0.name

        val functions = HashMap<String, Pair<Program, List<String>>>()
        for((name, piecewise) in item.arguments[1].extractAllPiecewise(variablesMap, unitsMap, variableMap, name=variable)) {
            functions.put(name, Pair(Program.generate(piecewise.first), piecewise.second))
        }

        return MathParse(variable, ParseTreeItem.generate(item.arguments[1].generateOffsetString(arg0, variablesMap, unitsMap, variableMap, name=variable)), false, functions)
    }

    if(arg0 is Diff) {
        try {
            if (arg0.bvar.variable.name == "time" && (arg0.bvar.degree == null || arg0.bvar.degree.evaluate() == 1.0)) {
                if(arg0.argument is Ci) {
                    val variable = variableMap[arg0.argument.name]?:arg0.argument.name

                    val functions = HashMap<String, Pair<Program, List<String>>>()
                    for((name, piecewise) in item.arguments[1].extractAllPiecewise(variablesMap, unitsMap, variableMap, name=variable)) {
                        functions.put(name, Pair(Program.generate(piecewise.first), piecewise.second))
                    }

                    return MathParse(variable, ParseTreeItem.generate(item.arguments[1].generateOffsetString(arg0, variablesMap, unitsMap, variableMap, name=variable)), true, functions)
                }

            }
        }
        catch(e: Exception) {}

        throw Exception("Only able to map flow constraints which are with respect to first order time")
    }


    return null
}

private fun HybridItem.parseVariables(variables: List<Variable>): List<String> {
    val privateVariables = ArrayList<String>()
    for(variable in variables) {
        val name = variable.name

        variableMap[this.name]!![name] = name
        if(this.variables.any { it.name.toLowerCase() == name.toLowerCase() }) {
            val count = this.variables.count { it.name.toLowerCase().startsWith(name.toLowerCase()) }
            variableMap[this.name]!![name] = "${name}${count}"
        }

        val locality = getLocalityForInterfaceType(variable.publicInterface)
        this.variables.add(HybridVariable(variableMap[this.name]!![name]!!, VariableType.REAL, locality))

        var hasPrivate = false
        if(variable.publicInterface == InterfaceType.NONE && variable.privateInterface != InterfaceType.NONE) {
            val privateLocality = getLocalityForInterfaceType(variable.privateInterface)
            this.variables.add(HybridVariable("${variableMap[this.name]!![name]!!}_private", VariableType.REAL, privateLocality))

            hasPrivate = true
        }
        else if(variable.publicInterface == InterfaceType.IN && variable.privateInterface == InterfaceType.OUT) {
            val privateLocality = getLocalityForInterfaceType(variable.privateInterface)
            this.variables.add(HybridVariable("${variableMap[this.name]!![name]!!}_private", VariableType.REAL, privateLocality))

            hasPrivate = true
        }
        else if(variable.publicInterface == InterfaceType.OUT && variable.privateInterface == InterfaceType.IN) {
            val privateLocality = getLocalityForInterfaceType(variable.privateInterface)
            this.variables.add(HybridVariable("${variableMap[this.name]!![name]!!}_private", VariableType.REAL, privateLocality))

            hasPrivate = true
        }

        if(hasPrivate) {
            privateVariables.add(variableMap[this.name]!![name]!!)
        }

        if(this is HybridAutomata)
            if(locality == Locality.EXTERNAL_OUTPUT || locality == Locality.INTERNAL)
                this.init.valuations[variableMap[this.name]!![name]!!] = ParseTreeItem.generate(variable.initialValue)
    }

    return privateVariables
}

private fun getLocalityForInterfaceType(interfaceType: InterfaceType): Locality {
    return when(interfaceType) {
        InterfaceType.IN -> Locality.EXTERNAL_INPUT
        InterfaceType.OUT -> Locality.EXTERNAL_OUTPUT
        InterfaceType.NONE -> Locality.INTERNAL
    }
}

private fun HybridNetwork.importConnections(connections: List<Connection>, components: List<Component>, encapsulationData: Map<String, EncapsulationData>) {
    for(connection in connections) {
        for(mapping in connection.variables) {
            val component1 = components.firstOrNull { it.name == connection.components.component1 }
            val component2 = components.firstOrNull { it.name == connection.components.component2 }

            if(component1 != null && component2 != null) {
                val variable1 = component1.variables?.firstOrNull { it.name == mapping.variable1 }
                val variable2 = component2.variables?.firstOrNull { it.name == mapping.variable2 }

                if(variable1 != null && variable2 != null) {
                    val encapsulation1 = encapsulationData[component1.name]
                    val encapsulation2 = encapsulationData[component2.name]

                    if(encapsulation1 != null && encapsulation2 != null) {
                        val (interface1, private1) = if(component2.name == encapsulation1.parent || encapsulation1.siblingSet.contains(component2.name))
                            Pair(variable1.publicInterface, false)
                        else if(encapsulation1.encapsulationSet.contains(component2.name) && variable1.privateInterface != InterfaceType.NONE && variable1.privateInterface != variable1.publicInterface)
                            Pair(variable1.privateInterface, true)
                        else if(encapsulation1.encapsulationSet.contains(component2.name))
                            Pair(variable1.publicInterface, false)
                        else
                            Pair(InterfaceType.NONE, false)

                        val (interface2, private2) = if(component1.name == encapsulation2.parent || encapsulation2.siblingSet.contains(component1.name))
                            Pair(variable2.publicInterface, false)
                        else if(encapsulation2.encapsulationSet.contains(component1.name) && variable2.privateInterface != InterfaceType.NONE && variable2.privateInterface != variable2.publicInterface)
                            Pair(variable2.privateInterface, true)
                        else if(encapsulation2.encapsulationSet.contains(component1.name))
                            Pair(variable2.publicInterface, false)
                        else
                            Pair(InterfaceType.NONE, false)

                        var variable1corrected = variableMap[component1.name]?.get(variable1.name) ?:variable1.name
                        var variable2corrected = variableMap[component2.name]?.get(variable2.name) ?:variable2.name

                        if(private1)
                            variable1corrected += "_private"

                        if(private2)
                            variable2corrected += "_private"

                        if(interface1 == InterfaceType.OUT && interface2 == InterfaceType.IN) {
                            val to = AutomataVariablePair(component2.name, variable2corrected)
                            val from = component1.name + "." + variable1corrected
                            this.ioMapping[to] = ParseTreeItem.generate(from)
                        }
                        else if(interface1 == InterfaceType.IN && interface2 == InterfaceType.OUT) {
                            val to = AutomataVariablePair(component1.name, variable1corrected)
                            val from = component2.name + "." + variable2corrected
                            this.ioMapping[to] = ParseTreeItem.generate(from)
                        }
                        else {
                            Logger.error("Unable to map variables '${component1.name}.${variable1.name}' to '${component2.name}.${variable2.name}'")
                        }
                    }
                }
                else {
                    if(variable1 == null)
                        Logger.error("Unable to find variable ${mapping.variable1} in ${component1.name}")

                    if(variable2 == null)
                        Logger.error("Unable to find variable ${mapping.variable2} in ${component2.name}")
                }
            }
            else {
                if(component1 == null)
                    Logger.error("Unable to find component ${connection.components.component1}")

                if(component2 == null)
                    Logger.error("Unable to find component ${connection.components.component2}")
            }
        }
    }

    val sortedMappings = this.ioMapping.toList().sortedWith(compareBy({ it.first.automata }, { it.first.variable })).toMap()
    this.ioMapping.clear()
    this.ioMapping.putAll(sortedMappings)
}

private fun parseGroups(groups: List<Group>?, components: List<String>): Map<String, EncapsulationData> {
    val encapsulationData = HashMap<String, EncapsulationData>()

    for(component in components) {
        encapsulationData.put(component, EncapsulationData())
    }

    if(groups != null) {
        for(group in groups) {
            if(group.relationships.any { it.relationship == RelationshipType.ENCAPSULATION }) {
                for(component in group.components) {
                    parseEncapsulationData(component, encapsulationData)
                }
            }
        }
    }

    for((comp, data) in encapsulationData.filter { it.value.parent == null }) {
        data.siblingSet.addAll(encapsulationData.filter { it.value.parent == null && it.key != comp }.keys)
    }

    for((comp, data) in encapsulationData) {
        data.hiddenSet.addAll(components.filter { it != comp && it != data.parent && !data.siblingSet.contains(it) && !data.encapsulationSet.contains(it) })
    }

    return encapsulationData
}

private fun parseEncapsulationData(component: ComponentRef, encapsulationData: HashMap<String, EncapsulationData>) {
    if(!encapsulationData.containsKey(component.component))
        throw Exception("Unknown component '${component.component}' in <group>")

    val parentData = encapsulationData[component.component]!!

    if(parentData.encapsulationSet.isNotEmpty())
        throw Exception("Multiple definitions for component '${component.component}' in <group>")

    if(component.components != null) {
        for(child in component.components) {
            if(!encapsulationData.containsKey(child.component))
                throw Exception("Unknown component '${child.component}' in <group>")

            val childData = encapsulationData[child.component]!!

            if(childData.parent != null)
                throw Exception("Multiple definitions for component '${child.component}' in <group>")

            childData.parent = component.component
            childData.siblingSet.clear()
            childData.siblingSet.addAll(component.components.map { it.component }.filter { it != child.component })

            parentData.encapsulationSet.add(child.component)

            parseEncapsulationData(child, encapsulationData)
        }
    }
}

data class EncapsulationData(
        val encapsulationSet: ArrayList<String> = arrayListOf(),
        var parent: String? = null,
        val siblingSet: ArrayList<String> = arrayListOf(),
        val hiddenSet: ArrayList<String> = arrayListOf()
)