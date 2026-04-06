package com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto;

import java.util.List;

public record JsonHealthyFinding(
        JsonDependencyId dependency,
        List<String> flags
) { }
