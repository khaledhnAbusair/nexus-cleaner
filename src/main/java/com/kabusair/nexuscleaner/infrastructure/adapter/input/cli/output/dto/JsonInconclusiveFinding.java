package com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto;

import java.util.List;

public record JsonInconclusiveFinding(
        JsonDependencyId dependency,
        String rationale,
        List<String> flags
) { }
