package com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto;

public record JsonDependencyId(
        String groupId,
        String artifactId,
        String version,
        String scope,
        boolean direct
) { }
