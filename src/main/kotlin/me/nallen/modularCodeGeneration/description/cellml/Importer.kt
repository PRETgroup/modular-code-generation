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
import java.util.*


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

            val network = createHybridNetwork(cellMLTree)

            val yamlMapper = YAMLMapper()
            println(yamlMapper.writeValueAsString(network))

            return Pair(network, Configuration())
        }
    }
}

private fun createHybridNetwork(model: Model): HybridNetwork {
    val network = HybridNetwork()

    network.name = model.name

    if(model.components != null)
        network.importComponents(model.components)

    if(model.connections != null)
        network.importConnections(model.connections)

    return network
}

private fun HybridNetwork.importComponents(components: List<Component>) {
    for(component in components) {
        val definitionId = UUID.randomUUID()
        val instantiateId = UUID.randomUUID()
        this.definitions.put(definitionId, createHybridAutomata(component))
        this.instances.put(component.name, AutomataInstance(instantiateId))
        this.instantiates.put(instantiateId, AutomataInstantiate(definitionId, component.name))
    }
}

private fun createHybridAutomata(component: Component): HybridAutomata {
    val item = HybridAutomata()

    item.name = component.name

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
