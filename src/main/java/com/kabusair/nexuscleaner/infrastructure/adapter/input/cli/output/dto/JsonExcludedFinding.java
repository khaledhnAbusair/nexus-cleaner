package com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto;

public record JsonExcludedFinding(
        JsonDependencyId dependency,
        String rationale
) { }
