package org.zeropointlauncher.launch.model;

import java.util.Optional;

public record ManifestFragment(
        String id,
        Optional<String> inheritsFrom,
        Optional<String> mainClass
) {}
