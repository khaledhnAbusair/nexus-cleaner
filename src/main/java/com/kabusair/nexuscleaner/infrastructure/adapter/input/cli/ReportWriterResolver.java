package com.kabusair.nexuscleaner.infrastructure.adapter.input.cli;

import com.kabusair.nexuscleaner.core.port.output.ReportWriter;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.ConsoleReportWriter;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.JsonReportWriter;

import java.io.PrintStream;

public final class ReportWriterResolver {

    public ReportWriter resolve(OutputFormat format, PrintStream out) {
        return switch (format) {
            case JSON -> new JsonReportWriter(out);
            case CONSOLE -> new ConsoleReportWriter(out);
        };
    }
}
