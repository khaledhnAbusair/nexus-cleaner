package com.kabusair.nexuscleaner;

import com.kabusair.nexuscleaner.bootstrap.ApplicationWiring;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.AuditCommand;

public final class NexusCleanerApplication {

    private NexusCleanerApplication() {
    }

    public static void main(String[] args) {
        ApplicationWiring wiring = new ApplicationWiring();
        int exitCode = AuditCommand.execute(args, wiring.auditUseCase(), wiring.reportWriterResolver());
        System.exit(exitCode);
    }
}
