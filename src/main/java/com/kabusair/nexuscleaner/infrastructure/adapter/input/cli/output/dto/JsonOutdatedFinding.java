package com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto;

import java.util.List;

public record JsonOutdatedFinding(
        JsonDependencyId dependency,
        String latestVersion,
        List<String> flags
) { }
