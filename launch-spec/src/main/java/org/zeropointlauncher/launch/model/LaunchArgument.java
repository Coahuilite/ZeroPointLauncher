package org.zeropointlauncher.launch.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.zeropointlauncher.launch.json.JsonSupport;

import java.util.List;
import java.util.Objects;

public sealed interface LaunchArgument {
    record Direct(String value) implements LaunchArgument {
        public Direct {
            if (value == null) {
                throw new IllegalArgumentException("参数值不能为 null");
            }
        }
    }

    record Conditional(
            @JsonDeserialize(contentUsing = JsonSupport.RuleDeserializer.class) List<Rule> rules,
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<String> value
    ) implements LaunchArgument {
        public Conditional {
            rules = rules == null
                    ? List.of()
                    : rules.stream().map(rule -> rule == null ? Rule.deny() : rule).toList();
            value = value == null
                    ? List.of()
                    : value.stream().filter(Objects::nonNull).toList();
        }
    }
}
