package com.kabusair.nexuscleaner.infrastructure.usecase;

import com.kabusair.nexuscleaner.core.domain.model.BuildSystem;
import com.kabusair.nexuscleaner.core.domain.model.DependencyCoordinate;
import com.kabusair.nexuscleaner.core.domain.model.ProjectContext;
import com.kabusair.nexuscleaner.core.usecase.ProjectContextFactory;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.build.BuildSystemDetector;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.build.gradle.GradleModuleDetector;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.build.maven.PomGavExtractor;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.build.maven.PomModuleDetector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Builds a {@link ProjectContext} by applying each build system's conventional
 * directory layout. For multi-module projects, recursively crawls all submodules
 * and aggregates their source/class/resource roots. Extracts all project GAVs
 * so the audit can exclude the project's own artifacts from findings.
 */
public final class DefaultProjectContextFactory implements ProjectContextFactory {

    private final BuildSystemDetector detector;
    private final PomModuleDetector pomModuleDetector;
    private final GradleModuleDetector gradleModuleDetector;
    private final PomGavExtractor gavExtractor;

    public DefaultProjectContextFactory(BuildSystemDetector detector,
                                        PomModuleDetector pomModuleDetector,
                                        GradleModuleDetector gradleModuleDetector,
                                        PomGavExtractor gavExtractor) {
        this.detector = detector;
        this.pomModuleDetector = pomModuleDetector;
        this.gradleModuleDetector = gradleModuleDetector;
        this.gavExtractor = gavExtractor;
    }

    @Override
    public ProjectContext create(Path projectRoot) {
        BuildSystem buildSystem = detector.detect(projectRoot);
        List<String> moduleNames = detectModules(projectRoot, buildSystem);
        List<Path> modulePaths = resolveModulePaths(projectRoot, moduleNames, buildSystem);
        Set<DependencyCoordinate> projectGavs = extractProjectGavs(projectRoot, moduleNames, buildSystem);
        return buildContext(projectRoot, buildSystem, moduleNames, modulePaths, projectGavs);
    }

    private List<String> detectModules(Path projectRoot, BuildSystem buildSystem) {
        return switch (buildSystem) {
            case MAVEN -> pomModuleDetector.detectModules(projectRoot.resolve("pom.xml"));
            case GRADLE -> gradleModuleDetector.detectModules(projectRoot);
        };
    }

    private Set<DependencyCoordinate> extractProjectGavs(Path projectRoot, List<String> moduleNames,
                                                         BuildSystem buildSystem) {
        if (buildSystem != BuildSystem.MAVEN) return Set.of();
        return gavExtractor.extractProjectGavs(projectRoot, moduleNames);
    }

    private List<Path> resolveModulePaths(Path projectRoot, List<String> moduleNames, BuildSystem buildSystem) {
        if (moduleNames.isEmpty()) return List.of(projectRoot);
        List<Path> paths = new ArrayList<>();
        collectModulePathsRecursive(projectRoot, moduleNames, buildSystem, paths);
        return paths;
    }

    private void collectModulePathsRecursive(Path parent, List<String> moduleNames,
                                             BuildSystem buildSystem, List<Path> out) {
        for (String moduleName : moduleNames) {
            Path modulePath = parent.resolve(moduleName);
            if (!Files.isDirectory(modulePath)) continue;
            List<String> childModules = detectChildModules(modulePath, buildSystem);
            if (childModules.isEmpty()) {
                out.add(modulePath);
            } else {
                collectModulePathsRecursive(modulePath, childModules, buildSystem, out);
            }
        }
    }

    private List<String> detectChildModules(Path modulePath, BuildSystem buildSystem) {
        return switch (buildSystem) {
            case MAVEN -> pomModuleDetector.detectModules(modulePath.resolve("pom.xml"));
            case GRADLE -> gradleModuleDetector.detectModules(modulePath);
        };
    }

    private ProjectContext buildContext(Path projectRoot,
                                       BuildSystem buildSystem,
                                       List<String> moduleNames,
                                       List<Path> modulePaths,
                                       Set<DependencyCoordinate> projectGavs) {
        List<Path> mainSrc = new ArrayList<>();
        List<Path> testSrc = new ArrayList<>();
        List<Path> mainCls = new ArrayList<>();
        List<Path> testCls = new ArrayList<>();
        List<Path> mainRes = new ArrayList<>();
        List<Path> testRes = new ArrayList<>();

        for (Path module : modulePaths) {
            addIfExists(mainSrc, module.resolve("src/main/java"));
            addIfExists(testSrc, module.resolve("src/test/java"));
            addIfExists(mainRes, module.resolve("src/main/resources"));
            addIfExists(testRes, module.resolve("src/test/resources"));
            mainCls.addAll(discoverMainClasses(module, buildSystem));
            testCls.addAll(discoverTestClasses(module, buildSystem));
        }

        return new ProjectContext(projectRoot, buildSystem, mainSrc, testSrc,
                mainCls, testCls, mainRes, testRes, moduleNames, projectGavs);
    }

    private List<Path> discoverMainClasses(Path moduleRoot, BuildSystem buildSystem) {
        if (buildSystem == BuildSystem.MAVEN) {
            return singletonIfExists(moduleRoot.resolve("target/classes"));
        }
        return firstExisting(
                moduleRoot.resolve("build/classes/java/main"),
                moduleRoot.resolve("build/classes/kotlin/main"));
    }

    private List<Path> discoverTestClasses(Path moduleRoot, BuildSystem buildSystem) {
        if (buildSystem == BuildSystem.MAVEN) {
            return singletonIfExists(moduleRoot.resolve("target/test-classes"));
        }
        return firstExisting(
                moduleRoot.resolve("build/classes/java/test"),
                moduleRoot.resolve("build/classes/kotlin/test"));
    }

    private void addIfExists(List<Path> list, Path candidate) {
        if (Files.isDirectory(candidate)) list.add(candidate);
    }

    private List<Path> singletonIfExists(Path candidate) {
        return Files.isDirectory(candidate) ? List.of(candidate) : List.of();
    }

    private List<Path> firstExisting(Path... candidates) {
        List<Path> existing = new ArrayList<>();
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) existing.add(candidate);
        }
        return List.copyOf(existing);
    }
}
