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
        {%- if not loop.last -%} ; {%- endif %} {%- if parameter.initialValueString %} -- {{ parameter.initialValueString }} {%- endif %}
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
        {{ variable.io }} : {{ variable.direction }} {{ variable.type }}
    {%- endif %}
{%- endfor %}

    );
end;

-- Architecture
architecture behavior of {{ item.name }} is
{%- if item.customFunctions|length > 0 %}
    -- Declare Custom Functions
    {%- for function in item.customFunctions %}
    function {{ function.name }}
    {%- if function.inputs|length > 0 %}(
        {%- for input in function.inputs -%}
            {%- if not loop.first %}; {% endif -%}
            {{ input.signal }}: {{ input.type }}
        {%- endfor -%}
    )
    {%- endif %}
            return {{ function.returnType }} is
        {%- for variable in function.variables %}
        variable {{ variable.signal }} : {{ variable.type }} := {{ variable.initialValue }}; {%- if variable.initialValueString %} -- {{ variable.initialValueString }} {%- endif %}
        {%- endfor %}
    begin
        {%- for line in function.logic %}
        {{ line }}
        {%- endfor %}
    end {{ function.name }};
    {%- endfor %}
{% endif %}

{%- if item.variables|length > 0 %}
    -- Declare all internal signals
{%- endif %}
{%- for variable in item.variables %}
    {%- if variable.locality == 'Internal Variables' %}
    signal {{ variable.signal }} : {{ variable.type }} := {{ variable.initialValue }}; {%- if variable.initialValueString %} -- {{ variable.initialValueString }} {%- endif %}
    {%- elif variable.direction == 'int' %}
    constant {{ variable.signal }} : {{ variable.type }} := {{ variable.initialValue }}; {%- if variable.initialValueString %} -- {{ variable.initialValueString }} {%- endif %}
    {%- endif %}
{%- endfor %}
{%- if item.variables|length > 0 %}
{% endif %}

{%- if config.runTimeParametrisation %}
    {%- for runtimeMappingProcess in item.runtimeMappingProcesses %}
    -- Signals for {{ runtimeMappingProcess.name }}
        {%- for variable in runtimeMappingProcess.variables %}
    signal {{ variable.signal }} : {{ variable.type }} := {{ variable.initialValue }}; {%- if variable.initialValueString %} -- {{ variable.initialValueString }} {%- endif %}
        {%- endfor %}
    signal {{ runtimeMappingProcess.processStartSignal }} : boolean := false;
    signal {{ runtimeMappingProcess.processDoneSignal }} : boolean := false;
    {% endfor %}
{%- endif %}

{%- if item.components|length > 0 %}
    -- Declare child components
{%- endif %}
{%- for component in item.components %}
    component {{ component.name }} is
    {%- if component.parameters|length > 0 %}
        generic(
        {%- for parameter in component.parameters %}
            {{ parameter.signal }} : {{ parameter.type }} := {{ parameter.initialValue }}
            {%- if not loop.last -%} ; {%- endif %} {%- if parameter.initialValueString %} -- {{ parameter.initialValueString }} {%- endif %}
        {%- endfor %}
        );
    {%- endif %}
        port(
            clk : in std_logic
    {%- if config.runTimeParametrisation and not component.automaton %};
            start : in boolean;
            finish : out boolean
    {%- endif %}
    {%- for variable in component.variables %}
        {%- if config.runTimeParametrisation or variable.locality == 'Inputs' or variable.locality == 'Outputs' %};
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
        variable count : integer range 0 to {{ runtimeMappingProcess.runtimeMappings|length + 1 }} := {{ runtimeMappingProcess.runtimeMappings|length + 1 }};
    begin
        if clk'event and clk = '1' then
            -- First let's do some transitions
            if count < {{ runtimeMappingProcess.runtimeMappings|length + 1 }} then
        {%- if runtimeMappingProcess.finishSignal != '' %}
                if {{ runtimeMappingProcess.finishSignal }} then
                    count := count + 1;
                end if;
        {%- else %}
                count := count + 1;
        {%- endif %}
            elsif count = {{ runtimeMappingProcess.runtimeMappings|length + 1 }} then
                if {{ runtimeMappingProcess.processStartSignal }} then
                    count := 0;
                end if;
            end if;

        {%- if runtimeMappingProcess.startSignal != '' %}
            if count < 1 then
                {{ runtimeMappingProcess.startSignal }} <= true;
            else
                {{ runtimeMappingProcess.startSignal }} <= false;
            end if;
        {%- endif %}

            -- Then, state logic
        {%- for i in range(2+runtimeMappingProcess.runtimeMappings|length) %}
            {% if i != 0 -%} els {%- endif -%}
            if count = {{ i }} then
                {%- if ((1+i+runtimeMappingProcess.runtimeMappings|length)%(2+runtimeMappingProcess.runtimeMappings|length)) < runtimeMappingProcess.runtimeMappings|length %}
                -- Map Outputs from previous iteration
                {%- for mapping in runtimeMappingProcess.runtimeMappings[((1+i+runtimeMappingProcess.runtimeMappings|length)%(2+runtimeMappingProcess.runtimeMappings|length))].mappingsOut %}
                {{ mapping.left }} <= {{ mapping.right }};
                {%- endfor %}
                {% endif %}

                {%- if ((1+i)%(2+runtimeMappingProcess.runtimeMappings|length)) < runtimeMappingProcess.runtimeMappings|length %}
                -- Map Inputs for next iteration
                {%- for mapping in runtimeMappingProcess.runtimeMappings[((1+i)%(2+runtimeMappingProcess.runtimeMappings|length))].mappingsIn %}
                {{ mapping.left }} <= {{ mapping.right }};
                {%- endfor %}
                {% endif %}

                {%- if i == runtimeMappingProcess.runtimeMappings|length %}
                -- We're done!
                {{ runtimeMappingProcess.processDoneSignal }} <= true;
                {% endif %}

                {%- if i == 0 %}
                -- We're starting the next run!
                {{ runtimeMappingProcess.processDoneSignal }} <= false;
                {% endif %}
        {%- endfor %}
            end if;
        end if;
    end process;
    {% endfor %}
    -- Perform Runtime mapping function
    process(clk)
        variable count : integer range 0 to 2 := 2;
    begin
        if clk'event and clk = '1' then
            if count = 0 then
    {%- for runtimeMappingProcess in item.runtimeMappingProcesses %}
                {{ runtimeMappingProcess.processStartSignal }} <= false;
    {%- endfor %}

                -- Wait until all sub-processes are done
                if {{ item.runtimeProcessDoneSignal }} then
                    count := 1;
                end if;
            elsif count = 1 then
                -- All the sub-processes have finished, let's do the mapping
    {%- for mapping in item.mappings %}
                {{ mapping.left }} <= {{ mapping.right }};
    {%- endfor %}

                finish <= true;

                count := 2;
            elsif count = 2 then
                -- Wait until we have to start again
                if start then
                    finish <= false;

    {%- for runtimeMappingProcess in item.runtimeMappingProcesses %}
                    {{ runtimeMappingProcess.processStartSignal }} <= true;
    {%- endfor %}

                    count := 0;
                end if;
            end if;

        end if;
    end process;
{%- else %}
    {%- if item.mappings|length > 0 %}
    -- Perform Mapping
        {%- for mapping in item.mappings %}
    {{ mapping.left }} <= {{ mapping.right }};
        {%- endfor %}
    {%- endif %}
{%- endif %}
end architecture;