library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

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
        finish : out boolean
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
{%- if item.variables|length > 0 %}
    -- Declare all internal signals
{%- endif %}
{%- for variable in item.variables %}
    {%- if variable.locality == 'Internal Variables' %}
    signal {{ variable.signal }} : {{ variable.type }} := {{ variable.initialValue }}; -- {{ variable.initialValueString }}
    {%- endif %}
{%- endfor %}
{%- if item.variables|length > 0 %}
{% endif %}

{%- if item.components|length > 0 %}
    -- Declare child components
{%- endif %}
{%- for component in item.components %}
    component {{ component.name }} is
    {%- if component.parameters|length > 0 %}
        generic(
        {%- for parameter in component.parameters %}
            {{ parameter.signal }} : {{ parameter.type }} := {{ parameter.initialValue }}
            {%- if not loop.last -%} ; {%- endif %} -- {{ parameter.initialValueString }}
        {%- endfor %}
        );
    {%- endif %}
        port(
            clk : in std_logic
    {%- if config.runTimeParametrisation %};
            start : in boolean;
            finish : out boolean
    {%- endif %}
    {%- for variable in component.variables %}
        {%- if variable.locality == 'Inputs' or variable.locality == 'Outputs' %};
            {{ variable.io }} : {{variable.direction }} {{ variable.type }}
        {%- endif %}
    {%- endfor %}
        );
    end component {{ component.name }};
{% endfor %}
begin

{%- if item.instances|length > 0 %}
    -- Create child instances
{%- endif %}
{%- for instance in item.instances %}
    {{ instance.id }} : component {{ instance.type }}
    {%- if instance.parameters|length > 0 %}
        generic map(
        {%- for mapping in instance.parameters %}
            {{ mapping.left }} => {{ mapping.right }}
            {%- if not loop.last -%} , {%- endif %}
        {%- endfor %}
        )
    {%- endif %}
        port map(
            clk => clk
    {%- for mapping in instance.mappings %},
            {{ mapping.left }} => {{ mapping.right }}
    {%- endfor %}
        );
{% endfor %}

{%- if config.runTimeParametrisation %}
    -- Perform Runtime functions for each instance
    {%- for runtimeMappingProcess in item.runtimeMappingProcesses %}
    {{ runtimeMappingProcess.name }}: process(clk)
        variable count : integer range 0 to {{ runtimeMappingProcess.runtimeMappings|length }} := {{ runtimeMappingProcess.runtimeMappings|length }};
    begin
        if clk'event and clk = '1' then
            -- First let's do some transitions
            if count < {{ runtimeMappingProcess.runtimeMappings|length }} then
                if {{ runtimeMappingProcess.finishSignal }} then
                    count := count + 1
                end if;
            elsif count = {{ runtimeMappingProcess.runtimeMappings|length }} then
                if start then
                    count := 0;
                    finish <= false;
                end if;
            end if;

            -- Then, state logic
        {%- for runtimeMapping in runtimeMappingProcess.runtimeMappings %}
            {% if not loop.first -%} els {%- endif -%}
            if count = {{ loop.index0 }} then
                {%- for mapping in runtimeMapping.mappings %}
                {{ mapping.left }} <= {{ mapping.right }};
                {%- endfor %}
        {%- endfor %}
            elsif count = {{ runtimeMappingProcess.runtimeMappings|length }} then
                {{ runtimeMappingProcess.processDoneSignal }} <= true;
            end if;
        end if;
    end process;
    {%- endfor %}

    -- Perform Runtime mapping function
    process(clk)
        variable count : integer range 0 to 2 := 2
    begin
        if clk'event and clk = '1' then
            if count = 0 then
                if {{ item.runtimeProcessDoneSignal }} then
                    count = 1;
                end if;
            elsif count = 1 then
    {%- for mapping in item.mappings %}
                {{ mapping.left }} <= {{ mapping.right }};
    {%- endfor %}

                count = 2;
            elsif count = 2 then
                if start then
                    count = 0;
                end if;
            end if;

        end if;
    end process;
{%- else %}
    {%- if item.mappings|length > 0 %}
    -- Perform Mapping
    process(clk)
    begin
        if clk'event and clk = '1' then
        {%- for mapping in item.mappings %}
            {{ mapping.left }} <= {{ mapping.right }};
        {%- endfor %}
        end if;
    end process;
    {%- endif %}
{%- endif %}
end architecture;