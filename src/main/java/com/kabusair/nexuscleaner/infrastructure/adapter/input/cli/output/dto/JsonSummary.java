package com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto;

public record JsonSummary(
        int total,
        int unused,
        int underused,
        int outdated,
        int healthy,
        int inconclusive,
        int excluded
) { }
