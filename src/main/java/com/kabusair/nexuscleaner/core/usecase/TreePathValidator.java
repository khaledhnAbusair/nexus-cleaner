package com.kabusair.nexuscleaner.core.usecase;

import com.kabusair.nexuscleaner.core.domain.model.DependencyCoordinate;
import com.kabusair.nexuscleaner.core.domain.model.DependencyGraph;
import com.kabusair.nexuscleaner.core.domain.model.DependencyNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Walks the full dependency tree to build a parent-to-child relationship map,
 * then determines whether a transitive dependency is exclusively reachable
 * through unused direct parents. A transitive is REMOVABLE_WITH_PARENT only
 * when EVERY path from a direct dependency to it passes through an unused node.
 * If even one live (non-unused) direct ancestor exists, the transitive survives
 * removal of the unused ones.
 */
public final class TreePathValidator {

    private final Map<String, Set<String>> parentsByChild;

    private TreePathValidator(Map<String, Set<String>> parentsByChild) {
        this.parentsByChild = parentsByChild;
    }

    /**
     * Builds the reverse index: for every node in the tree, records which
     * depth-1 (direct) dependencies are its ancestors. A node that appears
     * under multiple direct deps will have multiple entries.
     */
    public static TreePathValidator from(DependencyGraph graph) {
        Map<String, Set<String>> parentsByChild = new HashMap<>();
        for (DependencyNode root : graph.roots()) {
            for (DependencyNode directChild : root.children()) {
                String directGa = directChild.dependency().coordinate().ga();
                recordAncestry(directChild, directGa, parentsByChild);
            }
        }
        return new TreePathValidator(parentsByChild);
    }

    /**
     * Returns {@code true} only if every direct ancestor of this transitive
     * dependency is in the {@code unusedDirectGas} set. If even one direct
     * ancestor is alive, this transitive will survive and must NOT be flagged
     * as removable.
     */
    public boolean isExclusivelyUnderUnusedDirects(DependencyCoordinate transitiveCoord,
                                                   Set<String> unusedDirectGas) {
        String ga = transitiveCoord.ga();
        Set<String> directAncestors = parentsByChild.get(ga);
        if (directAncestors == null || directAncestors.isEmpty()) return false;
        for (String ancestor : directAncestors) {
            if (!unusedDirectGas.contains(ancestor)) return false;
        }
        return true;
    }

    private static void recordAncestry(DependencyNode node,
                                       String directAncestorGa,
                                       Map<String, Set<String>> parentsByChild) {
        String nodeGa = node.dependency().coordinate().ga();
        parentsByChild.computeIfAbsent(nodeGa, k -> new HashSet<>()).add(directAncestorGa);
        for (DependencyNode child : node.children()) {
            recordAncestry(child, directAncestorGa, parentsByChild);
        }
    }
}
