package com.kabusair.nexuscleaner.core.usecase.version;

import com.kabusair.nexuscleaner.core.domain.model.DependencyCoordinate;
import com.kabusair.nexuscleaner.core.port.output.VersionRegistry;

import java.util.Optional;

/**
 * Thin domain service that asks the configured {@link VersionRegistry} whether a
 * newer version of a dependency exists and normalizes the comparison.
 */
public final class VersionChecker {

    private final VersionRegistry registry;

    public VersionChecker(VersionRegistry registry) {
        this.registry = registry;
    }

    public Optional<String> latestIfNewer(DependencyCoordinate coord) {
        if (coord == null || coord.version() == null) return Optional.empty();
        Optional<String> latest = registry.latestVersion(coord);
        if (latest.isEmpty()) return Optional.empty();
        return isNewer(coord.version(), latest.get()) ? latest : Optional.empty();
    }

    private boolean isNewer(String current, String candidate) {
        if (current == null || candidate == null) return false;
        if (current.equals(candidate)) return false;
        return compareVersionStrings(current, candidate) < 0;
    }

    private int compareVersionStrings(String a, String b) {
        String[] aParts = a.split("[.\\-]");
        String[] bParts = b.split("[.\\-]");
        int len = Math.max(aParts.length, bParts.length);
        for (int i = 0; i < len; i++) {
            int cmp = compareSegment(segmentAt(aParts, i), segmentAt(bParts, i));
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    private String segmentAt(String[] parts, int i) {
        return i < parts.length ? parts[i] : "0";
    }

    private int compareSegment(String a, String b) {
        Integer ai = tryParseInt(a);
        Integer bi = tryParseInt(b);
        if (ai != null && bi != null) return Integer.compare(ai, bi);
        return a.compareTo(b);
    }

    private Integer tryParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
