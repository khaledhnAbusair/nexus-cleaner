package com.kabusair.nexuscleaner.core.domain.model;

import java.time.Instant;
import java.util.List;

/** The final output of one audit run. */
public record AuditReport(
        ProjectContext project,
        List<AuditFinding> findings,
        Instant generatedAt
) {
    public AuditReport {
        if (project == null) throw new IllegalArgumentException("project required");
        findings = findings == null ? List.of() : List.copyOf(findings);
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
    }

    public long countByHealth(DependencyHealth h) {
        return findings.stream().filter(f -> f.health() == h).count();
    }
}
