package com.kabusair.nexuscleaner.core.port.output;

import com.kabusair.nexuscleaner.core.domain.model.DependencyCoordinate;
import com.kabusair.nexuscleaner.core.domain.model.ProjectContext;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves a {@link DependencyCoordinate} to its on-disk JAR or classes directory.
 * Implementations may need to be initialized with project context before use.
 */
public interface LocalRepositoryLocator {

    Optional<Path> locate(DependencyCoordinate coordinate);

    /**
     * Called once before scanning begins. Implementations that need to shell out
     * to build tools or index project modules should do so here.
     * Default implementation is a no-op for backward compatibility.
     */
    default void initialize(ProjectContext project) { }
}
