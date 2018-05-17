package me.nallen.modularCodeGeneration.description.cellml

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.hybridAutomata.*
import me.nallen.modularCodeGeneration.parseTree.ParseTreeItem
import java.io.File
import java.lang.Math
import java.util.*
import kotlin.collections.HashMap


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
                BaseUnitInstance(BaseUnit("ampere"), -2.0))),
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
                BaseUnitInstance(BaseUnit("meter")),
                BaseUnitInstance(BaseUnit("second")),
                BaseUnitInstance(BaseUnit("ampere")))),
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

                            multiply *= baseUnits.multiply
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

    if(component.maths != null) {
        for(math in component.maths) {
            for(mathItem in math.items) {

            }
        }
    }

    item.init.state = "q0"

    item.locations.add(location)

    if(component.variables != null)
        item.parseVariables(component.variables)

    return item
}

private fun HybridItem.parseVariables(variables: List<Variable>) {
    for(variable in variables) {
        val name = variable.name

        val locality = when(variable.publicInterface) {
            InterfaceType.IN -> Locality.EXTERNAL_INPUT
            InterfaceType.OUT -> Locality.EXTERNAL_OUTPUT
            InterfaceType.NONE -> Locality.INTERNAL
        }

        this.addContinuousVariable(name, locality)

        if(this is HybridAutomata)
            this.init.valuations.put(name, ParseTreeItem.generate(variable.initialValue))
    }
}

private fun HybridNetwork.importConnections(connections: List<Connection>) {
    for(connection in connections) {
        for(mapping in connection.variables) {
            val to = AutomataVariablePair(connection.components.component1, mapping.variable1)
            val from = connection.components.component2 + "." + mapping.variable2
            this.ioMapping[to] = ParseTreeItem.generate(from)
        }
    }
}
