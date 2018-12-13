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

{%- if item.mappings|length > 0 %}
    -- Perform Mapping
{%- endif %}
{%- for mapping in item.mappings %}
    {{ mapping.left }} <= {{ mapping.right }};
{%- endfor %}
end architecture;