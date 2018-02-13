TARGET = {{ target }}
CC ?= gcc
BASEDIR ?= $(shell pwd)
CFLAGS ?= -c -O2 -Wall -I$(BASEDIR)
LDFLAGS ?= -g -Wall
LDLIBS ?= -lm
AR ?= ar
ARFLAGS ?= cr
AREXTRACT ?= x
OBJECTSDIR ?= Objects

export

build: $(TARGET)

{%- for object in objects -%}
{%- if object.type == "compile" %}

$(OBJECTSDIR)/{{ object.outputFile }}:{% for source in object.sources %} {{ source }}{% endfor %}{% for dep in object.dependencies %} {{ dep }}{% endfor %} $(BASEDIR)/config.h
	@echo Building {{ object.name }}...
	@mkdir -p $(OBJECTSDIR)
	@$(CC) $(CFLAGS) $< -o $@
{%- elif object.type == "subcall" %}

.PHONY: {{ object.outputDir }}/{{ object.outputFile }}
{{ object.outputDir }}/{{ object.outputFile }}:
	@$(MAKE) -C {{ object.outputDir }}/ {{ object.outputFile }}
{%- endif -%}
{%- endfor %}

$(TARGET):{% for object in objects %} {% if object.type == "subcall" %}{{ object.outputDir }}{% else %}$(OBJECTSDIR){% endif %}/{{ object.outputFile }}{% endfor %}
	@echo Building $(TARGET)...
	@rm -f $(TARGET)
{%- for object in objects -%}
    {%- if object.type == "subcall" %}
	@cd \$(OBJECTSDIR) && \$(AR) \$(AREXTRACT) ../{{ object.outputDir }}/{{ object.outputFile }}
    {%- endif -%}
{%- endfor %}
{%- if targetMethod == "link" %}
	@$(CC) $(LDFLAGS) $^ $(LDLIBS) -o $@
{%- elif targetMethod == "archive" -%}
	@$(AR) $(ARFLAGS) $(TARGET) $(OBJECTSDIR)/*.o
{%- endif %}

.PHONY: clean
clean:
	@echo Removing compiled binaries...
	@rm -rf $(TARGET) $(OBJECTSDIR)/* *~
{%- for object in objects -%}
    {%- if object.type == "subcall" %}
	@$(MAKE) -C {{ object.outputDir }}/ clean
    {%- endif -%}
{%- endfor %}