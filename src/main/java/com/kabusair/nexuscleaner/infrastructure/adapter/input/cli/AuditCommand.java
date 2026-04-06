package com.kabusair.nexuscleaner.infrastructure.adapter.input.cli;

import com.kabusair.nexuscleaner.core.domain.model.AuditReport;
import com.kabusair.nexuscleaner.core.domain.model.DependencyHealth;
import com.kabusair.nexuscleaner.core.port.input.AuditOptions;
import com.kabusair.nexuscleaner.core.port.input.AuditProjectUseCase;
import com.kabusair.nexuscleaner.core.port.output.ReportWriter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "audit",
        mixinStandardHelpOptions = true,
        description = "Audit a Maven or Gradle project for unused, underused, or outdated dependencies."
)
public final class AuditCommand implements Callable<Integer> {

    private static final int EXIT_OK = 0;
    private static final int EXIT_ISSUES_FOUND = 1;
    private static final int EXIT_ERROR = 2;

    private final AuditProjectUseCase useCase;
    private final ReportWriterResolver writerResolver;

    @Parameters(index = "0", description = "Project root directory", defaultValue = ".")
    private Path projectRoot;

    @Option(names = {"-f", "--format"}, description = "Output format: CONSOLE or JSON", defaultValue = "CONSOLE")
    private OutputFormat format;

    @Option(names = "--no-version-check", description = "Skip Maven Central lookups", defaultValue = "false")
    private boolean skipVersionCheck;

    @Option(names = "--offline", description = "Do not contact remote registries", defaultValue = "false")
    private boolean offline;

    @Option(names = "--include-test", description = "Include test-scope dependencies", defaultValue = "true")
    private boolean includeTestScope;

    @Option(names = "--underuse-threshold", description = "API usage ratio below which a dep is 'underused'", defaultValue = "0.05")
    private double underuseThreshold;

    @Option(names = "--fail-on-issues", description = "Exit with code 1 if UNUSED/OUTDATED findings exist", defaultValue = "false")
    private boolean failOnIssues;

    public AuditCommand(AuditProjectUseCase useCase, ReportWriterResolver writerResolver) {
        this.useCase = useCase;
        this.writerResolver = writerResolver;
    }

    @Override
    public Integer call() {
        try {
            return runAudit();
        } catch (RuntimeException e) {
            System.err.println("Audit failed: " + e.getMessage());
            return EXIT_ERROR;
        }
    }

    private Integer runAudit() {
        AuditOptions options = buildOptions();
        AuditReport report = useCase.audit(projectRoot.toAbsolutePath(), options);
        ReportWriter writer = writerResolver.resolve(format, System.out);
        writer.write(report);
        return exitCodeFor(report);
    }

    private AuditOptions buildOptions() {
        return new AuditOptions(includeTestScope, !skipVersionCheck, offline, underuseThreshold);
    }

    private Integer exitCodeFor(AuditReport report) {
        if (!failOnIssues) return EXIT_OK;
        long unused = report.countByHealth(DependencyHealth.UNUSED);
        long outdated = report.countByHealth(DependencyHealth.OUTDATED);
        return (unused + outdated) > 0 ? EXIT_ISSUES_FOUND : EXIT_OK;
    }

    public static int execute(String[] args, AuditProjectUseCase useCase, ReportWriterResolver resolver) {
        AuditCommand command = new AuditCommand(useCase, resolver);
        return new CommandLine(command).execute(args);
    }
}
