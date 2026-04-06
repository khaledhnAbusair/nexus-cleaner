package com.kabusair.nexuscleaner.core.port.output;

import com.kabusair.nexuscleaner.core.domain.model.DependencyCoordinate;

import java.util.Optional;

/** Looks up the latest published version of a dependency from an external registry. */
public interface VersionRegistry {

    Optional<String> latestVersion(DependencyCoordinate coordinate);
}
