#ifndef {{ item.macro }}_H_
#define {{ item.macro }}_H_

#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <math.h>

{%- if item.children|count > 0 %}
{% for child in item.children %}
    {%- ifchanged child.include %}
#include "{{ child.include }}"
    {%- endifchanged %}
{%- endfor %}
{%- endif %}

typedef int bool;
#define false 0
#define true 1

#include "config.h"

{%- if item.hasDelayed %}
#include "delayable.h"
{%- endif %}

{%- if item.enumName|length > 0 %}

// {{ item.name }} States
enum {{ item.enumName }} {
    {%- for location in item.locations %}
    {{ location }},
    {%- endfor %}
};
{%- endif %}

// {{ item.name }} Data Struct
typedef struct {
{%- for variable in item.variables %}
    {% ifchanged variable.locality %}
    // Declare {{ variable.locality }}
    {% endifchanged -%}
    {{ variable.type }} {{ variable.variable }};
{%- endfor %}

{%- if item.hasDelayed %}

    // Declare Delayed Variables
    {%- for variable in item.variables %}
        {%- if variable.delayed %}
    {{ variable.delayed.type }} {{variable.delayed.variable}};
        {%- endif %}
    {%- endfor %}
{%- endif %}

{%- if item.enumName|length > 0 %}

    // State
    enum {{ item.enumName }} state;
{%- endif %}

{%- if item.children|count > 0 %}

    // Declare Daughter Automata
    {%- for child in item.children %}
    {{ child.type }} {{child.variable}};
    {%- endfor %}
{%- endif %}
} {{ item.type }};

{%- if config.runTimeParametrisation %}

// {{ item.name }} Default Parametrisation function
void {{ item.paramFunction }}({{ item.type }}* me);
{%- endif %}

// {{ item.name }} Initialisation function
void {{ item.initFunction }}({{ item.type }}* me);

// {{ item.name }} Execution function
void {{ item.runFunction }}({{ item.type }}* me);

#endif // {{ item.macro }}_H_