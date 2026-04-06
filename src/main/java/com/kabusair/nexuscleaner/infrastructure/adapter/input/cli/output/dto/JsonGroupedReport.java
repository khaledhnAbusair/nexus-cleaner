package com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto;

public record JsonGroupedReport(
        JsonMetadata metadata,
        JsonGroupedResults results
) { }
