package com.kabusair.nexuscleaner.core.port.output;

import com.kabusair.nexuscleaner.core.domain.model.AuditReport;

/** Renders an {@link AuditReport} to some sink (stdout, file, JSON, markdown...). */
public interface ReportWriter {

    void write(AuditReport report);
}
