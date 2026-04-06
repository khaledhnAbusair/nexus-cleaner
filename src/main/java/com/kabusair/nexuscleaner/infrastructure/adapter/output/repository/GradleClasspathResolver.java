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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fully autonomous Gradle dependency path resolver. Injects a temporary init
 * script that iterates all configurations and prints each resolved artifact's
 * absolute path. Handles wrapper, custom repos, authentication transparently.
 */
public final class GradleClasspathResolver implements LocalRepositoryLocator {

    private static final Logger LOG = Logger.getLogger(GradleClasspathResolver.class.getName());
    private static final long TIMEOUT_SECONDS = 300;

    private static final String INIT_SCRIPT = """
            allprojects {
                task nexusCleanerClasspath {
                    doLast {
                        configurations.findAll { it.canBeResolved }.each { conf ->
                            try {
                                conf.resolvedConfiguration.resolvedArtifacts.each { art ->
                                    println "NCPATH:" + art.file.absolutePath
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
            """;

    private final ClasspathOutputParser parser;
    private Map<String, Path> resolvedPaths;

    public GradleClasspathResolver() {
        this.parser = new ClasspathOutputParser();
    }

    @Override
    public void initialize(ProjectContext project) {
        resolvedPaths = doResolve(project.rootDirectory());
        LOG.info("Gradle classpath resolver: " + resolvedPaths.size() + " JAR paths resolved");
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
        Path initScript = null;
        try {
            initScript = writeInitScript(projectRoot);
            List<String> output = runGradle(projectRoot, initScript);
            List<String> pathLines = extractPathLines(output);
            List<ClasspathEntry> entries = parser.parse(pathLines);
            return indexByKey(entries);
        } catch (RuntimeException | IOException e) {
            LOG.log(Level.WARNING, "Gradle classpath resolution failed: " + e.getMessage());
            return Map.of();
        } finally {
            deleteQuietly(initScript);
        }
    }

    private Path writeInitScript(Path projectRoot) throws IOException {
        Path script = projectRoot.resolve("build").resolve("nexuscleaner-init.gradle");
        Files.createDirectories(script.getParent());
        Files.writeString(script, INIT_SCRIPT, StandardCharsets.UTF_8);
        return script;
    }

    private List<String> runGradle(Path projectRoot, Path initScript) {
        String gradleExec = detectGradleExecutable(projectRoot);
        ProcessBuilder pb = new ProcessBuilder(
                gradleExec, "-q", "--no-daemon",
                "--init-script", initScript.toString(),
                "nexusCleanerClasspath")
                .directory(projectRoot.toFile())
                .redirectErrorStream(true);
        try {
            return executeAndRead(pb);
        } catch (IOException e) {
            throw new RuntimeException("Unable to invoke Gradle at '" + gradleExec + "'", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted waiting for Gradle", e);
        }
    }

    private List<String> executeAndRead(ProcessBuilder pb) throws IOException, InterruptedException {
        Process process = pb.start();
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            LOG.warning("Gradle classpath resolution timed out");
        }
        return lines;
    }

    private List<String> extractPathLines(List<String> output) {
        List<String> paths = new ArrayList<>();
        for (String line : output) {
            if (line.startsWith("NCPATH:")) {
                paths.add(line.substring(7));
            }
        }
        return paths;
    }

    private Map<String, Path> indexByKey(List<ClasspathEntry> entries) {
        Map<String, Path> map = new HashMap<>();
        for (ClasspathEntry entry : entries) {
            map.putIfAbsent(entry.fileKey(), entry.absolutePath());
        }
        return map;
    }

    private String detectGradleExecutable(Path projectRoot) {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path wrapper = projectRoot.resolve(windows ? "gradlew.bat" : "gradlew");
        if (Files.isExecutable(wrapper)) return wrapper.toString();
        return windows ? "gradle.bat" : "gradle";
    }

    private void deleteQuietly(Path file) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }
}
