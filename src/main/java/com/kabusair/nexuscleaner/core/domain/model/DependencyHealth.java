package com.kabusair.nexuscleaner.core.domain.model;

/** Audit verdict for a single dependency. */
public enum DependencyHealth {
    /** No usage evidence at any layer. Safe to remove. */
    UNUSED,
    /** Used, but less than 5% of its public API surface is referenced. Candidate for replacement. */
    UNDERUSED,
    /** Used and on the latest version. */
    HEALTHY,
    /** Used but a newer version is available. */
    OUTDATED,
    /** Intentionally excluded via {@code <exclusions>} in the consuming POM. */
    EXCLUDED,
    /**
     * Insufficient signal to decide safely. Typically applies to scopes or dependencies
     * where reflection is suspected. Never auto-removed.
     */
    INCONCLUSIVE
}
