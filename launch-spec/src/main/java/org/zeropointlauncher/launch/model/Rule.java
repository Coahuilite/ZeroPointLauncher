package org.zeropointlauncher.launch.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.zeropointlauncher.launch.json.JsonSupport;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Rule(
        Action action,
        Os os,
        @JsonDeserialize(using = JsonSupport.LenientFeaturesDeserializer.class)
        Map<String, Boolean> features
) {
    public Rule {
        action = action == null ? Action.DISALLOW : action;
        features = features == null ? Map.of() : Map.copyOf(features);
    }

    public static Rule deny() {
        return new Rule(Action.DISALLOW, null, Map.of());
    }

    public enum Action {
        ALLOW,
        DISALLOW;

        @JsonCreator
        public static Action fromJson(String value) {
            return "allow".equalsIgnoreCase(value) ? ALLOW : DISALLOW;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Os(String name, String arch, String version) {
        public Os {
            name = blankToNull(name);
            arch = blankToNull(arch);
            version = blankToNull(version);
        }

        private static String blankToNull(String value) {
            if (value == null) {
                return null;
            }
            String stripped = value.strip();
            return stripped.isEmpty() ? null : stripped;
        }
    }
}
