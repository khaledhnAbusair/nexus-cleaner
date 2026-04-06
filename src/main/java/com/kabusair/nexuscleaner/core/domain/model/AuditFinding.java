package com.kabusair.nexuscleaner.core.domain.model;

import java.util.List;
import java.util.Set;

public record AuditFinding(
        Dependency dependency,
        DependencyHealth health,
        List<UsageEvidence> evidence,
        String latestVersion,
        Set<FindingFlag> flags,
        String rationale
) {
    public AuditFinding {
        if (dependency == null) throw new IllegalArgumentException("dependency required");
        if (health == null) throw new IllegalArgumentException("health required");
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        flags = flags == null ? Set.of() : Set.copyOf(flags);
    }
}
