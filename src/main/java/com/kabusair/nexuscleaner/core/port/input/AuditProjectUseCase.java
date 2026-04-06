package com.kabusair.nexuscleaner.core.port.input;

import com.kabusair.nexuscleaner.core.domain.model.AuditReport;

import java.nio.file.Path;

public interface AuditProjectUseCase {

    AuditReport audit(Path projectRoot, AuditOptions options);
}
