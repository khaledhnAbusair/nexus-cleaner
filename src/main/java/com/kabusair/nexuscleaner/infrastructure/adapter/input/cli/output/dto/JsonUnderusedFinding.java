package com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto;

import java.util.List;

public record JsonUnderusedFinding(
        JsonDependencyId dependency,
        String usageRatio,
        String rationale,
        List<String> flags
) { }
