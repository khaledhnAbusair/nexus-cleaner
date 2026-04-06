package com.kabusair.nexuscleaner.core.port.output;

import com.kabusair.nexuscleaner.core.domain.model.BuildSystem;
import com.kabusair.nexuscleaner.core.domain.model.DependencyGraph;
import com.kabusair.nexuscleaner.core.domain.model.ProjectContext;

/**
 * Strategy port: resolves a project's full dependency tree (direct + transitive)
 * into a {@link DependencyGraph}. Implementations exist for Maven and Gradle.
 */
public interface DependencyGraphResolver {

    /** The build system this resolver handles. */
    BuildSystem supports();

    /** Build the complete dependency graph, with scopes and exclusions already applied. */
    DependencyGraph resolve(ProjectContext project);
}
