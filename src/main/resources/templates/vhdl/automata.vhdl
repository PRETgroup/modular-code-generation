library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

use work.config.all;

-- States
type {{ item.enumName }} is (
    {%- for location in item.locations %}
    {{ location.macroName }},
    {%- endfor %}
);

-- Entity
entity {{ item.name }} is
    port (
        clk : in std_logic;

{%- for variable in item.variables %}
    {%- if variable.locality != 'Parameters' %}
        {% ifchanged variable.locality %}
        -- Declare {{ variable.locality }}
        {% endifchanged -%}
        {{ variable.variable }} : {{ variable.type }};
    {%- endif %}
{%- endfor %}

    );
end;

architecture behavior of {{ item.name }} is
begin
    process(clk)
        -- Initialise State
        variable state : {{ item.enumName }} := {{ item.initialLocation }};

{%- for variable in item.variables %}
        {% ifchanged variable.locality %}
        -- Initialise {{ variable.locality }}
        {% endifchanged -%}
        variable {{ variable.variable }} : {{ variable.type }} := {{ variable.initialValue }}; -- {{ variable.initialValueString }}
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
                    state := {{ transition.nextState }};
            {%- endfor %}
            {%- if location.transitions|length > 0 %}
                end if;
            {%- endif %}

{%- endfor %}
{%- if item.locations|length > 0 %}
            end if;
{%- endif %}
        end if;
    end process;
end architecture;