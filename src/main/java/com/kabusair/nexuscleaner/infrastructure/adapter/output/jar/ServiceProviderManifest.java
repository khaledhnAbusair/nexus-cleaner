package com.kabusair.nexuscleaner.infrastructure.adapter.output.jar;

import java.util.Set;

public record ServiceProviderManifest(String serviceInterface, Set<String> implementations) {

    public ServiceProviderManifest {
        implementations = implementations == null ? Set.of() : Set.copyOf(implementations);
    }
}
