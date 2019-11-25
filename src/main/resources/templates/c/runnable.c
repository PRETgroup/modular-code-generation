#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include "config.h"

{%- if config.ccodeSettings.additionalHeaders|count > 0 %}
{% for additionalHeader in config.ccodeSettings.additionalHeaders %}
#include <{{ additionalHeader }}>
{%- endfor %}
{%- endif %}

#include "{{ item.include }}"

{{ item.type }} {{ item.variable }};

int main(void) {
{%- if config.runTimeParametrisation %}
    {{ item.paramFunction }}(&{{ item.variable }});
{%- endif %}
    {{ item.initFunction }}(&{{item.variable}});

#if ENABLE_LOGGING
    FILE* fp = fopen(LOGGING_FILE, "w");
    fprintf(fp, "Time{% for logging in loggingFields %},{{logging.name}}{% endfor %}\n");
    fprintf(fp, "%f{% for logging in loggingFields %},{{logging.formatString}}{% endfor %}\n", 0.0{% for logging in loggingFields %}, {{logging.field}}{% endfor %});
    unsigned int last_log = 0;
#endif

    unsigned int i = 0;
    for(i=1; i <= (SIMULATION_TIME / STEP_SIZE); i++) {
{%- if config.hasCLoopAnnotations %}
        {{ item.loopAnnotation }}
{%- endif %}
        {{ item.runFunction }}(&{{item.variable}});

        /* Logging */
#if ENABLE_LOGGING
        if((i - last_log) >= LOGGING_INTERVAL / STEP_SIZE) {
            fprintf(fp, "%f{% for logging in loggingFields %},{{logging.formatString}}{% endfor %}\n", i*STEP_SIZE{% for logging in loggingFields %}, {{logging.field}}{% endfor %});
            last_log = i;
        }
#endif
    }

    return 0;
}