library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

use work.config.all;
use work.lib.all;

-- Entity
entity {{ item.name }} is
{%- if item.parameters|length > 0 %}
    generic(
    {%- for parameter in item.parameters %}
        {{ parameter.signal }} : {{ parameter.type }} := {{ parameter.initialValue }}
        {%- if not loop.last -%} ; {%- endif %} -- {{ parameter.initialValueString }}
    {%- endfor %}
    );
{% endif %}
    port (
        clk : in std_logic
{%- if config.runTimeParametrisation %};
        start : in boolean;
        finish : out boolean;

        -- Declare State
        state : in {{ item.enumName }}
{%- endif %}

{%- for variable in item.variables %}
    {%- if variable.locality == 'Inputs' or variable.locality == 'Outputs' %};
        {% ifchanged variable.locality %}
        -- Declare {{ variable.locality }}
        {% endifchanged -%}
        {{ variable.io }} : {{variable.direction }} {{ variable.type }}
    {%- endif %}
{%- endfor %}

    );
end;

-- Architecture
architecture behavior of {{ item.name }} is
{%- if item.locations|length > 1 %}
    -- States
    type {{ item.enumName }} is (
{%- for location in item.locations %}
        {{ location.macroName }}
    {%- if not loop.last -%} , {%- endif %}
{%- endfor %}
    );

{%- if config.compileTimeParametrisation %}

    -- Declare State
    signal state : {{ item.enumName }} := {{ item.initialLocation }};
{%- endif %}
{%- endif %}

{%- for variable in item.variables %}
{%- if variable.locality != 'Inputs' %}
    {% ifchanged variable.locality %}
    -- Declare {{ variable.locality }}
    {% endifchanged -%}
    signal {{ variable.signal }} : {{ variable.type }} := {{ variable.initialValue }}; -- {{ variable.initialValueString }}
{%- endif %}
{%- endfor %}

{%- if item.customFunctions|length > 0 %}

    -- Declare Custom Functions
    {%- for function in item.customFunctions %}
    function {{ function.name }}(
        {%- for input in function.inputs -%}
            {%- if not loop.first %}, {% endif -%}
            {{ input.signal }}: {{ input.type }}
        {%- endfor -%}
    )
            return {{ function.returnType }} is
        {%- for variable in function.variables %}
        variable {{ variable.signal }} : {{ variable.type }} := {{ variable.initialValue }}; -- {{ variable.initialValueString }}
        {%- endfor %}
    begin
        {%- for line in function.logic %}
        {{ line }}
        {%- endfor %}
    end {{ function.name }};
    {%- endfor %}
{%- endif %}

begin

{%- for variable in item.variables %}
{%- if variable.locality == 'Outputs' %}
    {% ifchanged variable.locality %}
    -- Map {{ variable.locality }}
    {% endifchanged -%}
    {{ variable.io }} <= {{ variable.signal }};
{%- endif %}
{%- endfor %}

    process(clk)
{%- if item.locations|length > 1 %}
        -- Initialise State
        variable state_update : {{ item.enumName }} := {{ item.initialLocation }};
{%- endif %}

{%- for variable in item.variables %}
    {%- if variable.locality != 'Parameters' and variable.locality != 'Inputs' %}
        {% ifchanged variable.locality %}
        -- Initialise {{ variable.locality }}
        {% endifchanged -%}
        variable {{ variable.variable }} : {{ variable.type }} := {{ variable.initialValue }}; -- {{ variable.initialValueString }}
    {%- endif %}
{%- endfor %}

    begin
        if clk'event and clk = '1' then
{%- if item.locations|length > 1 %}
            -- Run the state machine for transition logic
    {%- for location in item.locations %}
            {% if not loop.first -%} els {%- endif -%}
            if state = {{ location.macroName }} then -- Logic for state {{ location.name }}

            {%- for transition in location.transitions %}
                {% if not loop.first -%} els {%- endif -%}
                if {{ transition.guard }} then

                    {%- if transition.flow|length > 0 %}
                    -- Perform Flow Operations
                    {%- endif %}

                    {%- for update in transition.flow %}
                    {{ update.variable }} := {{ update.equation }};
                    {%- endfor %}

                    {%- if transition.flow|length > 0 %}
                    {% endif %}

                    {%- if transition.update|length > 0 %}
                    -- Perform Update Operations
                    {%- endif %}

                    {%- for update in transition.update %}
                    {{ update.variable }} := {{ update.equation }};
                    {%- endfor %}

                    {%- if transition.update|length > 0 %}
                    {% endif %}

                    {%- if location.name != transition.nextStateName %}
                    -- Next state is {{ transition.nextStateName }}
                    state_update := {{ transition.nextState }};
                    {%- endif %}
            {% endfor %}
            {%- if location.transitions|length > 0 %}
                end if;
            {%- endif %}

    {%- endfor %}
            end if;

            -- Map State to Signal
            state <= state_update;
{%- elif item.locations|length > 0 %}
    {%- if item.locations[0].transitions|length > 1 %}
        {%- for transition in item.locations[0].transitions %}
            {% if not loop.first -%} els {%- endif -%}
            if {{ transition.guard }} then

                {%- if transition.flow|length > 0 %}
                -- Perform Flow Operations
                {%- endif %}

                {%- for update in transition.flow %}
                {{ update.variable }} := {{ update.equation }};
                {%- endfor %}

                {%- if transition.flow|length > 0 %}
                {% endif %}

                {%- if transition.update|length > 0 %}
                -- Perform Update Operations
                {%- endif %}

                {%- for update in transition.update %}
                {{ update.variable }} := {{ update.equation }};
                {%- endfor %}

                {%- if transition.update|length > 0 %}
                {% endif %}
        {% endfor %}
        {%- if location.transitions|length > 0 %}
            end if;
        {%- endif %}
    {%- elif item.locations[0].transitions|length > 0 %}
            {%- if item.locations[0].transitions[0].flow|length > 0 %}
            -- Perform Flow Operations
            {%- endif %}

            {%- for update in item.locations[0].transitions[0].flow %}
            {{ update.variable }} := {{ update.equation }};
            {%- endfor %}

            {%- if item.locations[0].transitions[0].flow|length > 0 %}
            {% endif %}

            {%- if item.locations[0].transitions[0].update|length > 0 %}
            -- Perform Update Operations
            {%- endif %}

            {%- for update in item.locations[0].transitions[0].update %}
            {{ update.variable }} := {{ update.equation }};
            {%- endfor %}

            {%- if item.locations[0].transitions[0].update|length > 0 %}
            {% endif %}
    {%- endif %}
{%- endif %}

{%- for variable in item.variables %}
    {%- if variable.locality != 'Parameters' and variable.locality != 'Inputs' %}
            {% ifchanged variable.locality %}
            -- Map {{ variable.locality }} to Signals
            {% endifchanged -%}
            {{ variable.signal }} <= {{ variable.variable }};
    {%- endif %}
{%- endfor %}

        end if;
    end process;
end architecture;