package com.kabusair.nexuscleaner.core.domain.model;

/**
 * Unified dependency scope across Maven and Gradle.
 *
 * <p>Scope awareness is load-bearing for accuracy: a {@link #PROVIDED} dependency
 * (e.g. Lombok, annotation processors, servlet-api) is expected to be absent from
 * the final bytecode. Marking it UNUSED purely from bytecode evidence would be a
 * false positive. See {@link #requiresBytecodeEvidence()}.
 */
public enum DependencyScope {
    COMPILE(true),
    PROVIDED(false),
    RUNTIME(true),
    TEST(true),
    SYSTEM(false),
    IMPORT(false);

    private final boolean bytecodeEvidenceExpected;

    DependencyScope(boolean bytecodeEvidenceExpected) {
        this.bytecodeEvidenceExpected = bytecodeEvidenceExpected;
    }

    /**
     * {@code true} if absence from bytecode is a meaningful "unused" signal for this scope.
     * Returns {@code false} for PROVIDED / SYSTEM / IMPORT — those can be fully used yet
     * leave no trace in the final {@code .class} files.
     */
    public boolean requiresBytecodeEvidence() {
        return bytecodeEvidenceExpected;
    }

    public static DependencyScope fromMaven(String raw) {
        if (raw == null || raw.isBlank()) return COMPILE;
        return switch (raw.trim().toLowerCase()) {
            case "compile" -> COMPILE;
            case "provided" -> PROVIDED;
            case "runtime" -> RUNTIME;
            case "test" -> TEST;
            case "system" -> SYSTEM;
            case "import" -> IMPORT;
            default -> COMPILE;
        };
    }
}
