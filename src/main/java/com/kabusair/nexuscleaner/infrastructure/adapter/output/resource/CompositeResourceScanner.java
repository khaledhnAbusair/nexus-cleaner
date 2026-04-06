package com.kabusair.nexuscleaner.infrastructure.adapter.output.resource;

import com.kabusair.nexuscleaner.core.domain.model.SymbolReference;
import com.kabusair.nexuscleaner.core.port.output.ResourceScanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Delegates to specialized extractors for each resource file type.
 * Walks all provided resource roots and source roots, identifying files
 * by name/path and routing them to the appropriate extractor.
 */
public final class CompositeResourceScanner implements ResourceScanner {

    private final PropertiesFqcnExtractor propertiesExtractor;
    private final YamlFqcnExtractor yamlExtractor;
    private final SpringFactoriesExtractor springFactoriesExtractor;
    private final ServiceFileExtractor serviceFileExtractor;
    private final PersistenceXmlExtractor persistenceXmlExtractor;
    private final SpringXmlExtractor springXmlExtractor;
    private final GlobalFqcnStringExtractor globalFqcnExtractor;
    private final CamelDslExtractor camelDslExtractor;

    public CompositeResourceScanner() {
        this.propertiesExtractor = new PropertiesFqcnExtractor();
        this.yamlExtractor = new YamlFqcnExtractor();
        this.springFactoriesExtractor = new SpringFactoriesExtractor();
        this.serviceFileExtractor = new ServiceFileExtractor();
        this.persistenceXmlExtractor = new PersistenceXmlExtractor();
        CamelUriMapper camelMapper = new CamelUriMapper();
        this.springXmlExtractor = new SpringXmlExtractor(camelMapper);
        this.globalFqcnExtractor = new GlobalFqcnStringExtractor();
        this.camelDslExtractor = new CamelDslExtractor(camelMapper);
    }

    @Override
    public Set<SymbolReference> scan(List<Path> resourceRoots) {
        if (resourceRoots == null || resourceRoots.isEmpty()) return Set.of();
        Set<SymbolReference> collected = new HashSet<>();
        for (Path root : resourceRoots) {
            if (!Files.isDirectory(root)) continue;
            scanRoot(root, collected);
        }
        return Set.copyOf(collected);
    }

    private void scanRoot(Path root, Set<SymbolReference> sink) {
        scanAllPropertiesFiles(root, sink);
        scanAllYamlFiles(root, sink);
        scanSpringFactories(root, sink);
        scanSpringImports(root, sink);
        scanMetaInfServices(root, sink);
        scanPersistenceXml(root, sink);
        scanAllXmlFiles(root, sink);
        scanAllSourceStrings(root, sink);
    }

    private void scanAllPropertiesFiles(Path root, Set<SymbolReference> sink) {
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".properties"))
                    .forEach(p -> propertiesExtractor.extract(p, sink));
        } catch (IOException ignored) {
        }
    }

    private void scanAllYamlFiles(Path root, Set<SymbolReference> sink) {
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".yml") || name.endsWith(".yaml");
                    })
                    .forEach(p -> yamlExtractor.extract(p, sink));
        } catch (IOException ignored) {
        }
    }

    private void scanSpringFactories(Path root, Set<SymbolReference> sink) {
        extractIfExists(root.resolve("META-INF/spring.factories"), sink, springFactoriesExtractor::extract);
    }

    private void scanSpringImports(Path root, Set<SymbolReference> sink) {
        Path springDir = root.resolve("META-INF/spring");
        if (!Files.isDirectory(springDir)) return;
        try (Stream<Path> files = Files.list(springDir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".imports"))
                    .forEach(p -> springFactoriesExtractor.extract(p, sink));
        } catch (IOException ignored) {
        }
    }

    private void scanMetaInfServices(Path root, Set<SymbolReference> sink) {
        Path servicesDir = root.resolve("META-INF/services");
        if (!Files.isDirectory(servicesDir)) return;
        try (Stream<Path> files = Files.list(servicesDir)) {
            files.filter(Files::isRegularFile)
                    .forEach(p -> serviceFileExtractor.extract(p, sink));
        } catch (IOException ignored) {
        }
    }

    private void scanPersistenceXml(Path root, Set<SymbolReference> sink) {
        extractIfExists(root.resolve("META-INF/persistence.xml"), sink, persistenceXmlExtractor::extract);
    }

    /**
     * Scans all XML files in the resource root for Spring bean declarations
     * and Camel URI endpoints. Covers applicationContext.xml, camel-context.xml,
     * beans.xml, and any custom Spring XML configs.
     */
    private static final java.util.Map<String, String> CONFIG_FILE_TO_FQCN = java.util.Map.of(
            "ehcache.xml", "org.ehcache.CacheManager",
            "ehcache3.xml", "org.ehcache.CacheManager",
            "hazelcast.xml", "com.hazelcast.core.HazelcastInstance",
            "infinispan.xml", "org.infinispan.manager.CacheContainer",
            "quartz.properties", "org.quartz.Scheduler"
    );

    private void scanAllXmlFiles(Path root, Set<SymbolReference> sink) {
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        if (name.endsWith(".xml")) {
                            springXmlExtractor.extract(p, sink);
                        }
                        String knownFqcn = CONFIG_FILE_TO_FQCN.get(name);
                        if (knownFqcn != null) {
                            sink.add(SymbolReference.reflectionHint(knownFqcn));
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    /**
     * Scans Java source files adjacent to resources (walks up to find src/main/java
     * from src/main/resources) for FQCN string literals. This catches Jupiter
     * Framework registry patterns and any dynamically referenced class names.
     */
    private void scanAllSourceStrings(Path root, Set<SymbolReference> sink) {
        Path sourceRoot = resolveSourceRootFromResourceRoot(root);
        if (sourceRoot == null || !Files.isDirectory(sourceRoot)) return;
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .forEach(p -> {
                        globalFqcnExtractor.extract(p, sink);
                        camelDslExtractor.extract(p, sink);
                    });
        } catch (IOException ignored) {
        }
    }

    private Path resolveSourceRootFromResourceRoot(Path resourceRoot) {
        Path parent = resourceRoot.getParent();
        if (parent == null) return null;
        return parent.resolve("java");
    }

    private void extractIfExists(Path file, Set<SymbolReference> sink, FileExtractor extractor) {
        if (Files.isRegularFile(file)) {
            extractor.extract(file, sink);
        }
    }

    @FunctionalInterface
    private interface FileExtractor {
        void extract(Path file, Set<SymbolReference> sink);
    }
}
