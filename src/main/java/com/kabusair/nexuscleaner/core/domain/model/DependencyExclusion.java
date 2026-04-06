package com.kabusair.nexuscleaner.core.domain.model;

import java.util.Objects;

/**
 * A user-declared exclusion. Supports Maven's {@code *} wildcard semantics: an exclusion
 * of {@code *:*} excludes all transitives; {@code com.foo:*} excludes every artifact of
 * that group. The presence of an exclusion is treated as <em>intentional</em> — matching
 * transitives are reported as {@link DependencyHealth#EXCLUDED}, never as unused.
 */
public record DependencyExclusion(String groupId, String artifactId) {

    public static final String WILDCARD = "*";

    public DependencyExclusion {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(artifactId, "artifactId");
    }

    public boolean matches(DependencyCoordinate coordinate) {
        return matchSegment(groupId, coordinate.groupId())
                && matchSegment(artifactId, coordinate.artifactId());
    }

    private static boolean matchSegment(String pattern, String value) {
        return WILDCARD.equals(pattern) || pattern.equals(value);
    }
}
