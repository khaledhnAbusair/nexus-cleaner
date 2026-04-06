package com.kabusair.nexuscleaner.core.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A node in the resolved dependency tree. Mutable during graph construction only. */
public final class DependencyNode {

    private final Dependency dependency;
    private final int depth;
    private final List<DependencyNode> children = new ArrayList<>();

    public DependencyNode(Dependency dependency, int depth) {
        this.dependency = dependency;
        this.depth = depth;
    }

    public Dependency dependency() {
        return dependency;
    }

    public int depth() {
        return depth;
    }

    public List<DependencyNode> children() {
        return Collections.unmodifiableList(children);
    }

    public void addChild(DependencyNode child) {
        children.add(child);
    }

    public boolean isRoot() {
        return depth == 0;
    }
}
