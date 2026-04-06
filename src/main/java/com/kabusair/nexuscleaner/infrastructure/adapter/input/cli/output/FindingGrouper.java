package com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output;

import com.kabusair.nexuscleaner.core.domain.model.AuditFinding;
import com.kabusair.nexuscleaner.core.domain.model.AuditReport;
import com.kabusair.nexuscleaner.core.domain.model.DependencyHealth;
import com.kabusair.nexuscleaner.core.domain.model.FindingFlag;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto.JsonDependencyId;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto.JsonExcludedFinding;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto.JsonGroupedReport;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto.JsonGroupedResults;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto.JsonHealthyFinding;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto.JsonInconclusiveFinding;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto.JsonMetadata;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto.JsonOutdatedFinding;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto.JsonSummary;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto.JsonUnderusedFinding;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto.JsonUnusedFinding;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transforms a flat {@link AuditReport} into a grouped {@link JsonGroupedReport}
 * with per-health-category arrays and only the fields relevant to each category.
 */
final class FindingGrouper {

    private static final Pattern RATIO_PATTERN = Pattern.compile("Usage ratio (\\S+)");

    JsonGroupedReport group(AuditReport report) {
        JsonMetadata metadata = buildMetadata(report);
        JsonGroupedResults results = buildResults(report);
        return new JsonGroupedReport(metadata, results);
    }

    private JsonMetadata buildMetadata(AuditReport report) {
        JsonSummary summary = new JsonSummary(
                report.findings().size(),
                countByHealth(report, DependencyHealth.UNUSED),
                countByHealth(report, DependencyHealth.UNDERUSED),
                countByHealth(report, DependencyHealth.OUTDATED),
                countByHealth(report, DependencyHealth.HEALTHY),
                countByHealth(report, DependencyHealth.INCONCLUSIVE),
                countByHealth(report, DependencyHealth.EXCLUDED));

        return new JsonMetadata(
                report.project().rootDirectory().toString(),
                report.project().buildSystem().name(),
                report.generatedAt().toString(),
                report.project().isMultiModule(),
                report.project().moduleNames(),
                report.project().mainSourceRoots().size(),
                report.project().testSourceRoots().size(),
                summary);
    }

    private JsonGroupedResults buildResults(AuditReport report) {
        List<JsonUnusedFinding> unused = new ArrayList<>();
        List<JsonUnderusedFinding> underused = new ArrayList<>();
        List<JsonOutdatedFinding> outdated = new ArrayList<>();
        List<JsonHealthyFinding> healthy = new ArrayList<>();
        List<JsonInconclusiveFinding> inconclusive = new ArrayList<>();
        List<JsonExcludedFinding> excluded = new ArrayList<>();

        for (AuditFinding f : report.findings()) {
            switch (f.health()) {
                case UNUSED -> unused.add(toUnused(f));
                case UNDERUSED -> underused.add(toUnderused(f));
                case OUTDATED -> outdated.add(toOutdated(f));
                case HEALTHY -> healthy.add(toHealthy(f));
                case INCONCLUSIVE -> inconclusive.add(toInconclusive(f));
                case EXCLUDED -> excluded.add(toExcluded(f));
            }
        }

        return new JsonGroupedResults(unused, underused, outdated, healthy, inconclusive, excluded);
    }

    private JsonUnusedFinding toUnused(AuditFinding f) {
        return new JsonUnusedFinding(toId(f), f.rationale(), toFlagNames(f));
    }

    private JsonUnderusedFinding toUnderused(AuditFinding f) {
        return new JsonUnderusedFinding(toId(f), extractUsageRatio(f), f.rationale(), toFlagNames(f));
    }

    private JsonOutdatedFinding toOutdated(AuditFinding f) {
        return new JsonOutdatedFinding(toId(f), f.latestVersion(), toFlagNames(f));
    }

    private JsonHealthyFinding toHealthy(AuditFinding f) {
        return new JsonHealthyFinding(toId(f), toFlagNames(f));
    }

    private JsonInconclusiveFinding toInconclusive(AuditFinding f) {
        return new JsonInconclusiveFinding(toId(f), f.rationale(), toFlagNames(f));
    }

    private JsonExcludedFinding toExcluded(AuditFinding f) {
        return new JsonExcludedFinding(toId(f), f.rationale());
    }

    private JsonDependencyId toId(AuditFinding f) {
        return new JsonDependencyId(
                f.dependency().coordinate().groupId(),
                f.dependency().coordinate().artifactId(),
                f.dependency().coordinate().version(),
                f.dependency().scope().name(),
                f.dependency().directlyDeclared());
    }

    private List<String> toFlagNames(AuditFinding f) {
        List<String> names = new ArrayList<>();
        for (FindingFlag flag : f.flags()) {
            names.add(flag.name());
        }
        return names;
    }

    private String extractUsageRatio(AuditFinding f) {
        if (f.rationale() == null) return null;
        Matcher m = RATIO_PATTERN.matcher(f.rationale());
        return m.find() ? m.group(1) : null;
    }

    private int countByHealth(AuditReport report, DependencyHealth health) {
        return (int) report.countByHealth(health);
    }
}
