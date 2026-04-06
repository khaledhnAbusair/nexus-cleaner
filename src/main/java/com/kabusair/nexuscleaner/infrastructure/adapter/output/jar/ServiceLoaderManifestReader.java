package com.kabusair.nexuscleaner.infrastructure.adapter.output.jar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Reads {@code META-INF/services/*} entries from a JAR. Every implementation
 * FQCN declared in a service file is returned so the matcher can treat it as a
 * reflective usage of the declaring JAR — this is how ServiceLoader-based
 * frameworks (JDBC drivers, logging backends, Jackson modules, etc.) register.
 */
public final class ServiceLoaderManifestReader {

    private static final String SERVICES_PREFIX = "META-INF/services/";

    public List<ServiceProviderManifest> readFrom(JarFile jar) {
        List<ServiceProviderManifest> manifests = new ArrayList<>();
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            collectIfServiceEntry(jar, entry, manifests);
        }
        return manifests;
    }

    private void collectIfServiceEntry(JarFile jar, JarEntry entry, List<ServiceProviderManifest> out) {
        if (entry.isDirectory()) return;
        String name = entry.getName();
        if (!name.startsWith(SERVICES_PREFIX)) return;
        String serviceInterface = name.substring(SERVICES_PREFIX.length());
        if (serviceInterface.isBlank()) return;
        Set<String> impls = readImplementationLines(jar, entry);
        out.add(new ServiceProviderManifest(serviceInterface, impls));
    }

    private Set<String> readImplementationLines(JarFile jar, JarEntry entry) {
        Set<String> impls = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                addIfValidImpl(impls, line);
            }
        } catch (IOException ignored) {
        }
        return impls;
    }

    private void addIfValidImpl(Set<String> impls, String line) {
        String trimmed = stripComment(line).trim();
        if (!trimmed.isEmpty()) {
            impls.add(trimmed);
        }
    }

    private String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash >= 0 ? line.substring(0, hash) : line;
    }
}
