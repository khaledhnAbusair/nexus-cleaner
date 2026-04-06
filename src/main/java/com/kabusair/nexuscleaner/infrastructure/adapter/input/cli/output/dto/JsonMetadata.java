package com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto;

import java.util.List;

public record JsonMetadata(
        String project,
        String buildSystem,
        String generatedAt,
        boolean multiModule,
        List<String> modules,
        int sourceRoots,
        int testRoots,
        JsonSummary summary
) { }
