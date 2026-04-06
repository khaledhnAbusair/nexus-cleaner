package com.kabusair.nexuscleaner.core.domain.model;

import java.util.Objects;

/**
 * Immutable GAV value object. Equality ignores version so that two coordinates
 * with the same groupId+artifactId are the same dependency for matching purposes.
 */
public record DependencyCoordinate(String groupId, String artifactId, String version) {

    public DependencyCoordinate {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(artifactId, "artifactId");
    }

    public String ga() {
        return groupId + ":" + artifactId;
    }

    public String gav() {
        return ga() + ":" + (version == null ? "" : version);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DependencyCoordinate c)) return false;
        return groupId.equals(c.groupId) && artifactId.equals(c.artifactId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId);
    }

    @Override
    public String toString() {
        return gav();
    }
}
