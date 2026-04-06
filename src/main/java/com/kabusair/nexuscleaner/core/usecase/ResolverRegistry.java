package com.kabusair.nexuscleaner.core.usecase;

import com.kabusair.nexuscleaner.core.domain.model.BuildSystem;
import com.kabusair.nexuscleaner.core.port.output.DependencyGraphResolver;

/**
 * Strategy selector: returns the {@link DependencyGraphResolver} registered for a
 * given build system, or {@code null} if none is available.
 */
public interface ResolverRegistry {

    DependencyGraphResolver resolverFor(BuildSystem buildSystem);
}
