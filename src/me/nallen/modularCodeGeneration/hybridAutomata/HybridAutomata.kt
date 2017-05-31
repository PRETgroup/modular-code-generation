package me.nallen.modularCodeGeneration.hybridAutomata

/**
 * Created by nall426 on 31/05/2017.
 */

data class HybridAutomata(
        var name: String = "HA",
        var locations: MutableList<Location> = ArrayList<Location>(),
        var edges: MutableList<Edge> = ArrayList<Edge>(),
        var init: Initialisation = Initialisation()
) {
    fun addLocation(location: Location) {
        locations.add(location)
    }
}

data class Location(
        var name: String = "",
        var invariant: String = "",
        var flow: String = "",
        var update: String = ""
) {

}

data class Edge(
        var fromLocation: Int = -1,
        var toLocation: Int = -1,
        var guard: String = "",
        var update: String = ""
) {

}

data class Initialisation(
        var state: Int = -1,
        var valuations: String = ""
) {

}
