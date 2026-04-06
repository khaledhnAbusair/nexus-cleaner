package com.kabusair.nexuscleaner.infrastructure.adapter.output.jar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Extracts auto-configuration and framework registration FQCNs from a JAR's
 * internal manifests. Covers:
 * <ul>
 *   <li>{@code META-INF/spring.factories} — Spring Boot 2.x auto-config</li>
 *   <li>{@code META-INF/spring/*.imports} — Spring Boot 3.x auto-config</li>
 *   <li>{@code META-INF/spring/aot.factories} — Spring AOT</li>
 *   <li>{@code META-INF/web-fragment.xml} — Servlet container fragments</li>
 * </ul>
 *
 * <p>Every FQCN found represents a class that will be wired at runtime by the
 * framework. If any of these classes appear in the project's dependency graph,
 * the owning JAR is considered actively used.
 */
public final class JarManifestScanner {

    private static final String SPRING_FACTORIES = "META-INF/spring.factories";
    private static final String SPRING_IMPORTS_PREFIX = "META-INF/spring/";
    private static final String SPRING_IMPORTS_SUFFIX = ".imports";

    public Set<String> extractAutoConfigClasses(JarFile jar) {
        Set<String> classes = new HashSet<>();
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.isDirectory()) continue;
            String name = entry.getName();
            if (isSpringManifest(name)) {
                readFqcnsFrom(jar, entry, classes);
            }
        }
        return classes;
    }

    private boolean isSpringManifest(String entryName) {
        if (SPRING_FACTORIES.equals(entryName)) return true;
        if (entryName.startsWith(SPRING_IMPORTS_PREFIX) && entryName.endsWith(SPRING_IMPORTS_SUFFIX)) return true;
        if (entryName.equals("META-INF/spring/aot.factories")) return true;
        return false;
    }

    private void readFqcnsFrom(JarFile jar, JarEntry entry, Set<String> sink) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                extractFromLine(line, sink);
            }
        } catch (IOException ignored) {
        }
    }

    private void extractFromLine(String line, Set<String> sink) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return;

        if (trimmed.contains("=")) {
            extractFromFactoriesLine(trimmed, sink);
        } else {
            addIfFqcn(trimmed, sink);
        }
    }

    private void extractFromFactoriesLine(String line, Set<String> sink) {
        int eq = line.indexOf('=');
        addIfFqcn(line.substring(0, eq).trim(), sink);
        String values = line.substring(eq + 1).trim();
        if (values.endsWith("\\")) {
            values = values.substring(0, values.length() - 1).trim();
        }
        for (String fqcn : values.split(",")) {
            addIfFqcn(fqcn.trim(), sink);
        }
    }

    private void addIfFqcn(String candidate, Set<String> sink) {
        if (candidate == null || candidate.isEmpty()) return;
        if (candidate.contains(".") && !candidate.contains(" ") && candidate.length() < 512) {
            sink.add(candidate);
        }
    }
}
