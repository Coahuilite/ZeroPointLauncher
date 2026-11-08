package org.zeropointlauncher.launch.model;

import java.util.Map;
import java.util.Optional;

public record Rule(Action action, Optional<OsRule> os, Map<String, Boolean> features) {
    public Rule {
        features = Map.copyOf(features);
    }

    public enum Action { ALLOW, DISALLOW }

    public record OsRule(
            Optional<String> name,
            Optional<String> arch,
            Optional<String> version
    ) {}
}
