package com.kabusair.nexuscleaner.infrastructure.adapter.output.jar;

import com.kabusair.nexuscleaner.core.domain.model.DependencyCoordinate;
import com.kabusair.nexuscleaner.core.domain.model.RelocationRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Heuristically infers shade-plugin relocations for an indexed JAR.
 *
 * <p>Given the dependency GAV (e.g. {@code com.google.guava:guava}) and the set of
 * packages actually present in the JAR, this detector reports a relocation
 * whenever the expected base package (derived from groupId) is absent but a
 * {@code *.shaded.*} or {@code *.internal.*} prefix-shifted variant is present.
 * The resulting rules let {@code JarIndexMatcher} resolve symbols written
 * against the original package names to their shaded counterparts.
 */
public final class RelocationDetector {

    private static final List<String> SHADE_MARKERS = List.of(".shaded.", ".shade.", ".internal.", ".repackaged.");

    public List<RelocationRule> detect(DependencyCoordinate coord, Set<String> packages) {
        String expectedBase = expectedBasePackage(coord);
        if (expectedBase == null || containsExpectedBase(packages, expectedBase)) {
            return List.of();
        }
        return findShadedCandidates(expectedBase, packages);
    }

    private String expectedBasePackage(DependencyCoordinate coord) {
        String groupId = coord.groupId();
        if (groupId == null || groupId.isBlank()) return null;
        return groupId;
    }

    private boolean containsExpectedBase(Set<String> packages, String expectedBase) {
        for (String pkg : packages) {
            if (pkg.equals(expectedBase) || pkg.startsWith(expectedBase + ".")) return true;
        }
        return false;
    }

    private List<RelocationRule> findShadedCandidates(String expectedBase, Set<String> packages) {
        List<RelocationRule> rules = new ArrayList<>();
        for (String pkg : packages) {
            addRuleIfShaded(rules, expectedBase, pkg);
        }
        return rules;
    }

    private void addRuleIfShaded(List<RelocationRule> rules, String expectedBase, String pkg) {
        String lastSegment = lastSegment(expectedBase);
        if (lastSegment == null) return;
        for (String marker : SHADE_MARKERS) {
            int idx = pkg.indexOf(marker + lastSegment);
            if (idx > 0) {
                String relocatedBase = pkg.substring(0, idx + marker.length() + lastSegment.length());
                rules.add(new RelocationRule(expectedBase, relocatedBase));
                return;
            }
        }
    }

    private String lastSegment(String packageName) {
        int dot = packageName.lastIndexOf('.');
        return dot < 0 ? packageName : packageName.substring(dot + 1);
    }
}
