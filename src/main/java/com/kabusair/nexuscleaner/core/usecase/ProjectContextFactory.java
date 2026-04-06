package com.kabusair.nexuscleaner.core.usecase;

import com.kabusair.nexuscleaner.core.domain.model.ProjectContext;

import java.nio.file.Path;

/**
 * Produces a {@link ProjectContext} from a project root. The concrete
 * implementation discovers source/class roots per build-system convention.
 */
public interface ProjectContextFactory {

    ProjectContext create(Path projectRoot);
}
