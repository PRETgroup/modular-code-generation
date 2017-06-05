package me.nallen.modularCodeGeneration.finiteStateMachine

import com.sun.org.apache.xml.internal.security.Init
import me.nallen.modularCodeGeneration.hybridAutomata.HybridAutomata
import me.nallen.modularCodeGeneration.parseTree.Literal
import me.nallen.modularCodeGeneration.parseTree.ParseTreeItem

/**
 * Created by nall426 on 31/05/2017.
 */

data class FiniteStateMachine(
        var name: String = "FSM",
        var states: MutableList<State> = ArrayList<State>(),
        var transitions: MutableList<Transition> = ArrayList<Transition>(),
        var init: Initialisation = Initialisation("")
) {

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

            for((fromLocation, toLocation, guard, update) in ha.edges) {
                fsm.addTransition(fromLocation, toLocation, guard, update)
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

        //TODO: Check if location introduces any new continuousVariables (invariant, flow, update)

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

        //TODO: Check if edge introduces any new continuousVariables (guard, update)

        //TODO: Check if edge introudces any new events

        transitions.add(transition)

        return this
    }

    fun setInit(init: Initialisation): FiniteStateMachine {
        if(!states.any({ it.name == init.state }))
            throw IllegalArgumentException("Location with name ${init.state} does not exist!")

        this.init = init

        return this
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