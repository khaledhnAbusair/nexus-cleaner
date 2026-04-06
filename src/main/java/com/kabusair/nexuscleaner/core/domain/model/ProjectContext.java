package com.kabusair.nexuscleaner.core.domain.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Everything the audit needs to know about a project's on-disk layout.
 * For multi-module projects, roots are the merged aggregate of all
 * discovered submodules. {@code projectGavs} contains the GAV coordinates
 * of the project itself and all its submodules — these are excluded from
 * the audit report since flagging your own code as "unused" is meaningless.
 */
public record ProjectContext(
        Path rootDirectory,
        BuildSystem buildSystem,
        List<Path> mainSourceRoots,
        List<Path> testSourceRoots,
        List<Path> mainClassRoots,
        List<Path> testClassRoots,
        List<Path> mainResourceRoots,
        List<Path> testResourceRoots,
        List<String> moduleNames,
        Set<DependencyCoordinate> projectGavs
) {
    public ProjectContext {
        Objects.requireNonNull(rootDirectory, "rootDirectory");
        Objects.requireNonNull(buildSystem, "buildSystem");
        mainSourceRoots   = safeList(mainSourceRoots);
        testSourceRoots   = safeList(testSourceRoots);
        mainClassRoots    = safeList(mainClassRoots);
        testClassRoots    = safeList(testClassRoots);
        mainResourceRoots = safeList(mainResourceRoots);
        testResourceRoots = safeList(testResourceRoots);
        moduleNames       = safeList(moduleNames);
        projectGavs       = projectGavs == null ? Set.of() : Set.copyOf(projectGavs);
    }

    public boolean isMultiModule() {
        return !moduleNames.isEmpty();
    }

    public boolean isSelfGav(DependencyCoordinate coord) {
        return projectGavs.contains(coord);
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : List.copyOf(list);
    }
}
