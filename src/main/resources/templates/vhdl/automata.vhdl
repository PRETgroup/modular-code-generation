library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

use work.config.all;

-- States
type {{ item.enumName }} is (
    {%- for location in item.locations %}
    {{ location.macroName }}
    {%- if not loop.last -%} , {%- endif %}
    {%- endfor %}
);

-- Entity
entity {{ item.name }} is
    port (
        clk : in std_logic;

{%- for variable in item.variables %}
    {%- if variable.locality == 'Inputs' or variable.locality == 'Outputs'  %}
        {% ifchanged variable.locality %}
        -- Declare {{ variable.locality }}
        {% endifchanged -%}
        {{ variable.io }} : {{variable.direction }} {{ variable.type }};
    {%- endif %}
{%- endfor %}

    );
end;

-- Architecture
architecture behavior of {{ item.name }} is
    -- Declare State
    signal state : {{ item.enumName }} := {{ item.initialLocation }};

{%- for variable in item.variables %}
{%- if variable.locality != 'Inputs' %}
    {% ifchanged variable.locality %}
    -- Declare {{ variable.locality }}
    {% endifchanged -%}
    signal {{ variable.signal }} : {{ variable.type }} := {{ variable.initialValue }}; -- {{ variable.initialValueString }}
{%- endif %}
{%- endfor %}

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
        -- Initialise State
        variable state_update : {{ item.enumName }} := {{ item.initialLocation }};

{%- for variable in item.variables %}
    {%- if variable.locality != 'Parameters' and variable.locality != 'Inputs' %}
        {% ifchanged variable.locality %}
        -- Initialise {{ variable.locality }}
        {% endifchanged -%}
        variable {{ variable.variable }} : {{ variable.type }} := {{ variable.initialValue }}; -- {{ variable.initialValueString }}
    {%- endif %}
{%- endfor %}

    begin
        if(clk'event and clk = '1') then
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
                    {%- else %}
                    -- Remain in this state
                    {%- endif %}
                    state_update := {{ transition.nextState }};
            {% endfor %}
            {%- if location.transitions|length > 0 %}
                end if;
            {%- endif %}

{%- endfor %}
{%- if item.locations|length > 0 %}
            end if;
{%- endif %}

            -- Map State to Signal
            state <= state_update;

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