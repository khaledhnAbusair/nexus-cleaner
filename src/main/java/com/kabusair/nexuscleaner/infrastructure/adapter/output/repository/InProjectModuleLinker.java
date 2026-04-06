package com.kabusair.nexuscleaner.infrastructure.adapter.output.repository;

import com.kabusair.nexuscleaner.core.domain.model.DependencyCoordinate;
import com.kabusair.nexuscleaner.core.domain.model.ProjectContext;
import com.kabusair.nexuscleaner.core.port.output.LocalRepositoryLocator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Handles sibling sub-module dependencies that may not be in the local Maven
 * repo yet. For each sub-module whose {@code target/classes} exists, maps the
 * artifactId to that directory so the indexer can scan compiled output directly.
 */
public final class InProjectModuleLinker implements LocalRepositoryLocator {

    private static final Logger LOG = Logger.getLogger(InProjectModuleLinker.class.getName());

    private Map<String, Path> moduleClassesByArtifactId;

    @Override
    public void initialize(ProjectContext project) {
        moduleClassesByArtifactId = new HashMap<>();
        if (!project.isMultiModule()) return;

        Path root = project.rootDirectory();
        for (String moduleName : project.moduleNames()) {
            indexModule(root, moduleName);
        }
        LOG.info("In-project linker: " + moduleClassesByArtifactId.size() + " modules with target/classes");
    }

    @Override
    public Optional<Path> locate(DependencyCoordinate coordinate) {
        if (moduleClassesByArtifactId == null || coordinate == null) return Optional.empty();
        Path classesDir = moduleClassesByArtifactId.get(coordinate.artifactId());
        if (classesDir != null && Files.isDirectory(classesDir)) return Optional.of(classesDir);
        return Optional.empty();
    }

    private void indexModule(Path root, String moduleName) {
        Path classesDir = root.resolve(moduleName).resolve("target/classes");
        if (!Files.isDirectory(classesDir)) return;
        moduleClassesByArtifactId.put(moduleName, classesDir);
    }
}
