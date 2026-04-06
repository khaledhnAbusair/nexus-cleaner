package com.kabusair.nexuscleaner.infrastructure.adapter.output.repository;

import com.kabusair.nexuscleaner.core.domain.model.DependencyCoordinate;
import com.kabusair.nexuscleaner.core.domain.model.ProjectContext;
import com.kabusair.nexuscleaner.core.port.output.LocalRepositoryLocator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fully autonomous Maven dependency path resolver. Shells out to
 * {@code mvn dependency:build-classpath} which handles settings.xml, mirrors,
 * private Nexus/Artifactory repos, encrypted passwords, and custom local repo
 * locations — all without any configuration from the user.
 *
 * <p>Called once per audit run via {@link #initialize(ProjectContext)}; the
 * resolved path map is cached for the lifetime of the locator.
 */
public final class MavenClasspathResolver implements LocalRepositoryLocator {

    private static final Logger LOG = Logger.getLogger(MavenClasspathResolver.class.getName());
    private static final long TIMEOUT_SECONDS = 300;

    private final ClasspathOutputParser parser;
    private final String mavenExecutable;
    private Map<String, Path> resolvedPaths;

    public MavenClasspathResolver(String mavenExecutable) {
        this.mavenExecutable = mavenExecutable;
        this.parser = new ClasspathOutputParser();
    }

    @Override
    public void initialize(ProjectContext project) {
        resolvedPaths = doResolve(project.rootDirectory());
        LOG.info("Maven classpath resolver: " + resolvedPaths.size() + " JAR paths resolved");
    }

    @Override
    public Optional<Path> locate(DependencyCoordinate coordinate) {
        if (resolvedPaths == null || coordinate == null) return Optional.empty();
        String key = coordinate.artifactId() + "-" + coordinate.version();
        Path path = resolvedPaths.get(key);
        if (path != null && Files.isRegularFile(path)) return Optional.of(path);
        return Optional.empty();
    }

    private Map<String, Path> doResolve(Path projectRoot) {
        try {
            List<String> output = runMaven(projectRoot);
            List<ClasspathEntry> entries = parser.parse(output);
            return indexByKey(entries);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Maven classpath resolution failed, falling back to empty map: " + e.getMessage());
            return Map.of();
        }
    }

    private Map<String, Path> indexByKey(List<ClasspathEntry> entries) {
        Map<String, Path> map = new HashMap<>();
        for (ClasspathEntry entry : entries) {
            map.putIfAbsent(entry.fileKey(), entry.absolutePath());
        }
        return map;
    }

    private List<String> runMaven(Path projectRoot) {
        Path outputFile = outputFilePath(projectRoot);
        ProcessBuilder pb = new ProcessBuilder(
                mavenExecutable, "-B", "-q",
                "dependency:resolve",
                "dependency:build-classpath",
                "-DincludeScope=test",
                "-Dmdep.outputFile=" + outputFile)
                .directory(projectRoot.toFile())
                .redirectErrorStream(true);
        try {
            return executeAndRead(pb, outputFile);
        } catch (IOException e) {
            throw new RuntimeException("Unable to invoke Maven at '" + mavenExecutable + "'", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted waiting for Maven", e);
        }
    }

    private List<String> executeAndRead(ProcessBuilder pb, Path outputFile) throws IOException, InterruptedException {
        Process process = pb.start();
        drainStream(process);
        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            LOG.warning("Maven classpath resolution timed out after " + TIMEOUT_SECONDS + "s");
            return List.of();
        }
        return readOutputFile(outputFile);
    }

    private void drainStream(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            while (reader.readLine() != null) { }
        }
    }

    private Path outputFilePath(Path projectRoot) {
        return projectRoot.resolve("target").resolve("nexuscleaner-classpath.txt");
    }

    private List<String> readOutputFile(Path outputFile) {
        if (!Files.isRegularFile(outputFile)) {
            LOG.warning("Maven classpath output file not found: " + outputFile);
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(outputFile, StandardCharsets.UTF_8);
            Files.deleteIfExists(outputFile);
            return lines;
        } catch (IOException e) {
            LOG.warning("Failed to read Maven classpath output: " + e.getMessage());
            return List.of();
        }
    }

    public static String detectMavenExecutable() {
        String override = System.getenv("NEXUSCLEANER_MVN");
        if (override != null && !override.isBlank()) return override;
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return windows ? "mvn.cmd" : "mvn";
    }
}
