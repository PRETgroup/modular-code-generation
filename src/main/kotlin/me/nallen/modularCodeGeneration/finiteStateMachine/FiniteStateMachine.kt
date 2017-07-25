package me.nallen.modularCodeGeneration.finiteStateMachine

import me.nallen.modularCodeGeneration.hybridAutomata.HybridAutomata
import me.nallen.modularCodeGeneration.hybridAutomata.Locality as HybridLocality
import me.nallen.modularCodeGeneration.parseTree.Literal
import me.nallen.modularCodeGeneration.parseTree.Variable as ParseTreeVariable
import me.nallen.modularCodeGeneration.parseTree.ParseTreeItem
import me.nallen.modularCodeGeneration.parseTree.generateString
import me.nallen.modularCodeGeneration.parseTree.getChildren

/**
 * Created by nall426 on 31/05/2017.
 */

data class FiniteStateMachine(
        var name: String = "FSM"
) {
    val states = ArrayList<State>()
    val transitions = ArrayList<Transition>()
    var init = Initialisation("")

    val variables = ArrayList<Variable>()

    companion object Factory {
        fun generateFromHybridAutomata(ha: HybridAutomata): FiniteStateMachine {
            val fsm = FiniteStateMachine()

            fsm.name = ha.name

            for((name, invariant, flow, update) in ha.locations) {
                fsm.addState(name)

                val updates = HashMap<String, ParseTreeItem>()
                updates.putAll(flow)
                updates.putAll(update)

                fsm.addTransition(name, name, invariant, updates)
            }

            for((fromLocation, toLocation, guard, update, inEvents, outEvents) in ha.edges) {
                // Combine Guard
                var combinedGuard = guard
                if(inEvents !is Literal || inEvents.value == "false") {
                    combinedGuard = ParseTreeItem.generate("(${guard.generateString()}) && (${inEvents.generateString()})")
                }

                // Combine Output
                val combinedUpdate = HashMap<String, ParseTreeItem>()
                combinedUpdate.putAll(update)
                for(event in outEvents) {
                    combinedUpdate.put(event, Literal("true"))
                }

                fsm.addTransition(fromLocation, toLocation, combinedGuard, combinedUpdate)
            }

            for((name, locality) in ha.continuousVariables) {
                fsm.addVariableIfNotExist(
                        name,
                        VariableType.REAL,
                        Locality.createFromHybridLocality(locality)
                )
            }

            for((name, locality) in ha.events) {
                fsm.addVariableIfNotExist(
                        name,
                        VariableType.BOOLEAN,
                        Locality.createFromHybridLocality(locality)
                )
            }

            fsm.setInit(Initialisation(ha.init.state, ha.init.valuations))

            return fsm
        }
    }

    fun addState(
            name: String
    ): FiniteStateMachine {
        return addState(State(name))
    }

    fun addState(state: State): FiniteStateMachine {
        if(states.any({it.name == state.name}))
            throw IllegalArgumentException("Location with name ${state.name} already exists!")

        states.add(state)

        return this
    }

    fun addTransition(
            fromLocation: String,
            toLocation: String,
            guard: ParseTreeItem = Literal("true"),
            update: Map<String, ParseTreeItem> = HashMap<String, ParseTreeItem>()
    ): FiniteStateMachine {
        return addTransition(Transition(fromLocation, toLocation, guard, update))
    }

    fun addTransition(transition: Transition): FiniteStateMachine {
        if(!states.any({ it.name == transition.fromLocation }))
            throw IllegalArgumentException("Location with name ${transition.fromLocation} does not exist!")

        if(!states.any({ it.name == transition.toLocation }))
            throw IllegalArgumentException("Location with name ${transition.toLocation} does not exist!")

        for((fromLocation, toLocation, guard) in transitions) {
            if(fromLocation == transition.fromLocation
                    && toLocation == transition.toLocation && guard == transition.guard) {
                //TODO: Should become a WARN once logging implemented
                throw IllegalArgumentException("Edge with same (from, to, guard) " +
                        "($fromLocation, $toLocation, $guard pairing already exists!")

                //TODO: Should be updated to merge updates if different
            }
        }

        transitions.add(transition)

        return this
    }

    fun setInit(init: Initialisation): FiniteStateMachine {
        if(!states.any({ it.name == init.state }))
            throw IllegalArgumentException("Location with name ${init.state} does not exist!")

        this.init = init

        return this
    }

    /* Private Methods */

    protected fun addVariableIfNotExist(
            item: String,
            type: VariableType = VariableType.REAL,
            locality: Locality = Locality.INTERNAL
    ) {
        if(!variables.any({it.name == item})) {
            variables.add(Variable(item, type, locality))
        }
    }
}

data class State(
        var name: String
)

data class Transition(
        var fromLocation: String,
        var toLocation: String,
        var guard: ParseTreeItem = Literal("true"),
        var update: Map<String, ParseTreeItem> = HashMap<String, ParseTreeItem>()
)

data class Initialisation(
        var state: String,
        var valuations: Map<String, ParseTreeItem> = HashMap<String, ParseTreeItem>()
)

data class Variable(
        var name: String,
        var type: VariableType,
        var locality: Locality
)

enum class VariableType {
    BOOLEAN, REAL
}

enum class Locality {
    INTERNAL, EXTERNAL_INPUT, EXTERNAL_OUTPUT, PARAMETER;

    companion object {
        fun createFromHybridLocality(locality: HybridLocality): Locality {
            return when(locality) {
                HybridLocality.INTERNAL -> Locality.INTERNAL
                HybridLocality.EXTERNAL_INPUT -> Locality.EXTERNAL_INPUT
                HybridLocality.EXTERNAL_OUTPUT -> Locality.EXTERNAL_OUTPUT
                HybridLocality.PARAMETER -> Locality.PARAMETER
            }
        }
    }

    fun getTextualName(): String {
        return when(this) {
            Locality.INTERNAL -> "Internal Variables"
            Locality.EXTERNAL_INPUT -> "Inputs"
            Locality.EXTERNAL_OUTPUT -> "Outputs"
            Locality.PARAMETER -> "Parameters"
        }
    }
}
