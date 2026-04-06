package com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto;

import java.util.List;

public record JsonGroupedResults(
        List<JsonUnusedFinding> unused,
        List<JsonUnderusedFinding> underused,
        List<JsonOutdatedFinding> outdated,
        List<JsonHealthyFinding> healthy,
        List<JsonInconclusiveFinding> inconclusive,
        List<JsonExcludedFinding> excluded
) { }
