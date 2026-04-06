package com.kabusair.nexuscleaner.core.domain.model;

import java.util.Set;

/**
 * A resolved dependency entry: coordinate + scope + its declared exclusions.
 *
 * <p>{@code directlyDeclared} distinguishes dependencies the user wrote in their
 * POM/build script from transitives pulled in by the resolver. Audit findings for
 * transitives are advisory; direct ones are actionable.
 */
public record Dependency(
        DependencyCoordinate coordinate,
        DependencyScope scope,
        boolean optional,
        boolean directlyDeclared,
        Set<DependencyExclusion> exclusions
) {
    public Dependency {
        if (coordinate == null) throw new IllegalArgumentException("coordinate required");
        if (scope == null) scope = DependencyScope.COMPILE;
        exclusions = exclusions == null ? Set.of() : Set.copyOf(exclusions);
    }
}
