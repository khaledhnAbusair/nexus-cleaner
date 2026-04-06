package com.kabusair.nexuscleaner.core.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aggregate root for the resolved dependency graph of one project. Holds the
 * tree and an index of user-declared exclusions; the resolver has already
 * dropped excluded transitives from the tree, but we keep the rules so the
 * report can mark matching entries as {@link DependencyHealth#EXCLUDED} rather
 * than silently hiding them.
 */
public final class DependencyGraph {

    private final List<DependencyNode> roots;
    private final Map<String, Dependency> byGa;
    private final Set<DependencyExclusion> exclusions;

    private DependencyGraph(List<DependencyNode> roots,
                            Map<String, Dependency> byGa,
                            Set<DependencyExclusion> exclusions) {
        this.roots = List.copyOf(roots);
        this.byGa = Collections.unmodifiableMap(byGa);
        this.exclusions = Set.copyOf(exclusions);
    }

    public static DependencyGraph of(List<DependencyNode> roots, Set<DependencyExclusion> exclusions) {
        Map<String, Dependency> byGa = new LinkedHashMap<>();
        for (DependencyNode root : roots) {
            collect(root, byGa);
        }
        return new DependencyGraph(roots, byGa, exclusions == null ? Set.of() : exclusions);
    }

    private static void collect(DependencyNode node, Map<String, Dependency> acc) {
        acc.putIfAbsent(node.dependency().coordinate().ga(), node.dependency());
        for (DependencyNode child : node.children()) {
            collect(child, acc);
        }
    }

    public List<DependencyNode> roots() {
        return roots;
    }

    public Map<String, Dependency> allByGa() {
        return byGa;
    }

    public Set<DependencyExclusion> exclusions() {
        return exclusions;
    }
}
