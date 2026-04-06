package com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output;

import com.kabusair.nexuscleaner.core.domain.model.AuditFinding;
import com.kabusair.nexuscleaner.core.domain.model.AuditReport;
import com.kabusair.nexuscleaner.core.domain.model.DependencyHealth;
import com.kabusair.nexuscleaner.core.domain.model.FindingFlag;
import com.kabusair.nexuscleaner.core.port.output.ReportWriter;

import java.io.PrintStream;

/**
 * Human-readable console writer. Groups findings by health bucket, prints the
 * rationale, and lists any finding flags (reflection suspicion, relocation
 * matches, etc.) so the user understands why a verdict was reached.
 */
public final class ConsoleReportWriter implements ReportWriter {

    private final PrintStream out;

    public ConsoleReportWriter(PrintStream out) {
        this.out = out;
    }

    @Override
    public void write(AuditReport report) {
        printHeader(report);
        printBucket(report, DependencyHealth.UNUSED,       "UNUSED");
        printBucket(report, DependencyHealth.UNDERUSED,    "UNDERUSED");
        printBucket(report, DependencyHealth.OUTDATED,     "OUTDATED");
        printBucket(report, DependencyHealth.EXCLUDED,     "EXCLUDED");
        printBucket(report, DependencyHealth.INCONCLUSIVE, "INCONCLUSIVE");
        printSummary(report);
    }

    private void printHeader(AuditReport report) {
        out.println("NexusCleaner audit");
        out.println("Project : " + report.project().rootDirectory());
        out.println("Build   : " + report.project().buildSystem());
        if (report.project().isMultiModule()) {
            out.println("Modules : " + report.project().moduleNames().size()
                    + " (" + String.join(", ", report.project().moduleNames()) + ")");
            out.println("Sources : " + report.project().mainSourceRoots().size() + " main roots, "
                    + report.project().testSourceRoots().size() + " test roots");
        }
        out.println("When    : " + report.generatedAt());
        out.println();
    }

    private void printBucket(AuditReport report, DependencyHealth health, String label) {
        long count = report.countByHealth(health);
        if (count == 0) return;
        out.println("[" + label + "] " + count);
        for (AuditFinding finding : report.findings()) {
            if (finding.health() == health) printFinding(finding);
        }
        out.println();
    }

    private void printFinding(AuditFinding finding) {
        out.println("  - " + finding.dependency().coordinate().gav()
                + "  (scope=" + finding.dependency().scope() + ")");
        if (finding.rationale() != null && !finding.rationale().isBlank()) {
            out.println("      reason : " + finding.rationale());
        }
        printFlags(finding);
        printLatest(finding);
    }

    private void printFlags(AuditFinding finding) {
        if (finding.flags().isEmpty()) return;
        StringBuilder sb = new StringBuilder("      flags  : ");
        boolean first = true;
        for (FindingFlag flag : finding.flags()) {
            if (!first) sb.append(", ");
            sb.append(flag);
            first = false;
        }
        out.println(sb.toString());
    }

    private void printLatest(AuditFinding finding) {
        if (finding.latestVersion() == null) return;
        out.println("      latest : " + finding.latestVersion());
    }

    private void printSummary(AuditReport report) {
        out.println("Summary");
        out.println("  total        : " + report.findings().size());
        out.println("  unused       : " + report.countByHealth(DependencyHealth.UNUSED));
        out.println("  underused    : " + report.countByHealth(DependencyHealth.UNDERUSED));
        out.println("  outdated     : " + report.countByHealth(DependencyHealth.OUTDATED));
        out.println("  healthy      : " + report.countByHealth(DependencyHealth.HEALTHY));
        out.println("  inconclusive : " + report.countByHealth(DependencyHealth.INCONCLUSIVE));
        out.println("  excluded     : " + report.countByHealth(DependencyHealth.EXCLUDED));
    }
}
