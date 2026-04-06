package com.kabusair.nexuscleaner.infrastructure.adapter.output.repository;

import com.kabusair.nexuscleaner.core.domain.model.DependencyCoordinate;
import com.kabusair.nexuscleaner.core.port.output.LocalRepositoryLocator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves a {@link DependencyCoordinate} to its JAR file in the standard
 * {@code ~/.m2/repository} layout. Honors the {@code M2_REPO} environment
 * variable and falls back to the default user home location.
 */
public final class MavenLocalRepositoryLocator implements LocalRepositoryLocator {

    private final Path repositoryRoot;

    public MavenLocalRepositoryLocator() {
        this(defaultRepositoryRoot());
    }

    public MavenLocalRepositoryLocator(Path repositoryRoot) {
        this.repositoryRoot = repositoryRoot;
    }

    @Override
    public Optional<Path> locate(DependencyCoordinate coordinate) {
        if (coordinate == null || coordinate.version() == null) return Optional.empty();
        Path jar = jarPathFor(coordinate);
        return Files.isRegularFile(jar) ? Optional.of(jar) : Optional.empty();
    }

    private Path jarPathFor(DependencyCoordinate coordinate) {
        Path groupDir = groupIdToPath(coordinate.groupId());
        String fileName = coordinate.artifactId() + "-" + coordinate.version() + ".jar";
        return repositoryRoot.resolve(groupDir).resolve(coordinate.artifactId()).resolve(coordinate.version()).resolve(fileName);
    }

    private Path groupIdToPath(String groupId) {
        String[] segments = groupId.split("\\.");
        Path p = repositoryRoot;
        for (String segment : segments) {
            p = p.resolve(segment);
        }
        return repositoryRoot.relativize(p);
    }

    private static Path defaultRepositoryRoot() {
        String override = System.getenv("M2_REPO");
        if (override != null && !override.isBlank()) return Path.of(override);
        return Path.of(System.getProperty("user.home"), ".m2", "repository");
    }
}
