package com.kabusair.nexuscleaner.infrastructure.adapter.output.jar;

import com.kabusair.nexuscleaner.core.domain.exception.NexusCleanerException;
import com.kabusair.nexuscleaner.core.domain.model.DependencyCoordinate;
import com.kabusair.nexuscleaner.core.domain.model.JarIndex;
import com.kabusair.nexuscleaner.core.domain.model.RelocationRule;
import com.kabusair.nexuscleaner.core.port.output.JarIndexer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Indexes a JAR file or exploded classes directory into a {@link JarIndex}.
 * Collects exported classes, SPI impls, shade relocations, auto-config
 * manifests (spring.factories / spring/*.imports), annotation processor
 * status, and framework/starter classification.
 */
public final class LocalJarIndexer implements JarIndexer {

    private static final String CLASS_SUFFIX = ".class";
    private static final String MODULE_INFO = "module-info.class";
    private static final String ANNOTATION_PROCESSOR_SPI = "META-INF/services/javax.annotation.processing.Processor";

    private final ServiceLoaderManifestReader manifestReader;
    private final RelocationDetector relocationDetector;
    private final JarManifestScanner manifestScanner;
    private final FrameworkDetector frameworkDetector;

    public LocalJarIndexer(ServiceLoaderManifestReader manifestReader,
                           RelocationDetector relocationDetector,
                           JarManifestScanner manifestScanner,
                           FrameworkDetector frameworkDetector) {
        this.manifestReader = manifestReader;
        this.relocationDetector = relocationDetector;
        this.manifestScanner = manifestScanner;
        this.frameworkDetector = frameworkDetector;
    }

    @Override
    public JarIndex index(Path path, DependencyCoordinate coordinate) {
        if (Files.isDirectory(path)) {
            return indexDirectory(path, coordinate);
        }
        return indexJarFile(path, coordinate);
    }

    private JarIndex indexJarFile(Path jarFile, DependencyCoordinate coordinate) {
        try (JarFile jar = new JarFile(jarFile.toFile())) {
            Set<String> exported = new HashSet<>();
            Set<String> packages = new HashSet<>();
            boolean isAP = false;

            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (ANNOTATION_PROCESSOR_SPI.equals(name)) isAP = true;
                if (isScannableClass(name)) {
                    String fqcn = pathToFqcn(name);
                    exported.add(fqcn);
                    addPackageOf(fqcn, packages);
                }
            }

            Set<String> spiImpls = collectServiceImpls(jar);
            Set<String> autoConfig = manifestScanner.extractAutoConfigClasses(jar);
            List<RelocationRule> relocations = relocationDetector.detect(coordinate, packages);
            boolean isFramework = frameworkDetector.isStarterOrFramework(coordinate, exported.size());

            return new JarIndex(coordinate, exported, relocations, exported.size(),
                    spiImpls, isAP, autoConfig, isFramework);
        } catch (IOException e) {
            throw new NexusCleanerException("Failed to index JAR " + jarFile, e);
        }
    }

    private JarIndex indexDirectory(Path classesDir, DependencyCoordinate coordinate) {
        Set<String> exported = new HashSet<>();
        Set<String> packages = new HashSet<>();
        boolean isAP = Files.isRegularFile(classesDir.resolve(ANNOTATION_PROCESSOR_SPI));

        try (Stream<Path> walk = Files.walk(classesDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(CLASS_SUFFIX))
                    .filter(p -> !p.getFileName().toString().equals(MODULE_INFO))
                    .forEach(p -> {
                        String relative = classesDir.relativize(p).toString();
                        if (!relative.startsWith("META-INF")) {
                            String fqcn = pathToFqcn(relative);
                            exported.add(fqcn);
                            addPackageOf(fqcn, packages);
                        }
                    });
        } catch (IOException e) {
            throw new NexusCleanerException("Failed to index directory " + classesDir, e);
        }

        List<RelocationRule> relocations = relocationDetector.detect(coordinate, packages);
        return new JarIndex(coordinate, exported, relocations, exported.size(),
                Set.of(), isAP, Set.of(), false);
    }

    private boolean isScannableClass(String entryName) {
        if (!entryName.endsWith(CLASS_SUFFIX)) return false;
        if (entryName.equals(MODULE_INFO)) return false;
        return !entryName.startsWith("META-INF/");
    }

    private String pathToFqcn(String relativePath) {
        String withoutSuffix = relativePath.substring(0, relativePath.length() - CLASS_SUFFIX.length());
        return withoutSuffix.replace('/', '.').replace('\\', '.');
    }

    private void addPackageOf(String fqcn, Set<String> packages) {
        int dot = fqcn.lastIndexOf('.');
        if (dot > 0) packages.add(fqcn.substring(0, dot));
    }

    private Set<String> collectServiceImpls(JarFile jar) {
        Set<String> all = new HashSet<>();
        List<ServiceProviderManifest> manifests = manifestReader.readFrom(jar);
        for (ServiceProviderManifest m : manifests) {
            all.addAll(m.implementations());
        }
        return all;
    }
}
