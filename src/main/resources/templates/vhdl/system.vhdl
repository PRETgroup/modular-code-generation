library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

use work.lib.all;

-- Entity
entity {{ item.name }} is
    port (
        clk : in std_logic

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
    signal {{ variable.signal }} : {{ variable.type }} := {{ variable.initialValue }}; {%- if variable.initialValueString %} -- {{ variable.initialValueString }} {%- endif %}
    {%- endif %}
{%- endfor %}
{%- if item.variables|length > 0 %}
{% endif %}

    -- Declare base component
    component {{ item.component.name }} is
    {%- if item.component.parameters|length > 0 %}
        generic(
        {%- for parameter in item.component.parameters %}
            {{ parameter.signal }} : {{ parameter.type }} := {{ parameter.initialValue }}
            {%- if not loop.last -%} ; {%- endif %} {%- if parameter.initialValueString %} -- {{ parameter.initialValueString }} {%- endif %}
        {%- endfor %}
        );
    {%- endif %}
        port(
            clk : in std_logic
    {%- if config.runTimeParametrisation %};
            start : in boolean;
            finish : out boolean
    {%- endif %}
    {%- for variable in item.component.variables %}
        {%- if variable.locality == 'Inputs' or variable.locality == 'Outputs' %};
            {{ variable.io }} : {{variable.direction }} {{ variable.type }}
        {%- endif %}
    {%- endfor %}
        );
    end component {{ item.component.name }};
begin
    -- Create base instance
    {{ item.instance.id }} : component {{ item.instance.type }}
    {%- if item.instance.parameters|length > 0 %}
        generic map(
        {%- for mapping in item.instance.parameters %}
            {{ mapping.left }} => {{ mapping.right }}
            {%- if not loop.last -%} , {%- endif %}
        {%- endfor %}
        )
    {%- endif %}
        port map(
            clk => clk
    {%- if config.runTimeParametrisation %},
            start => true,
    {%- endif %}
    {%- for mapping in item.instance.mappings %},
            {{ mapping.left }} => {{ mapping.right }}
    {%- endfor %}
        );

{%- if item.mappings|length > 0 %}

    -- Perform Mapping
{%- endif %}
{%- for mapping in item.mappings %}
    {{ mapping.left }} <= {{ mapping.right }};
{%- endfor %}
end architecture;