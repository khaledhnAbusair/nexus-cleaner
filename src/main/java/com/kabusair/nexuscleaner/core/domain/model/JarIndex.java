package com.kabusair.nexuscleaner.core.domain.model;

import java.util.List;
import java.util.Set;

/**
 * Pure data carrier describing the contents of one indexed JAR.
 *
 * <p>{@code autoConfigClasses} holds FQCNs declared in the JAR's own
 * {@code META-INF/spring.factories} or {@code META-INF/spring/*.imports}.
 * These classes are wired by Spring Boot at runtime and constitute evidence
 * that the JAR is actively participating in the application — even when no
 * user code imports anything from it directly.
 *
 * <p>{@code isStarterOrFramework} is {@code true} when the JAR is a known
 * meta-dependency (e.g. spring-boot-starter-*) that carries no code of its
 * own but exists to pull in a curated set of transitives. Flagging such
 * JARs as UNUSED would be misleading.
 */
public record JarIndex(
        DependencyCoordinate coordinate,
        Set<String> exportedClasses,
        List<RelocationRule> relocations,
        int publicApiSize,
        Set<String> serviceImpls,
        boolean isAnnotationProcessor,
        Set<String> autoConfigClasses,
        boolean isStarterOrFramework
) {
    public JarIndex {
        exportedClasses   = exportedClasses   == null ? Set.of() : Set.copyOf(exportedClasses);
        relocations       = relocations       == null ? List.of() : List.copyOf(relocations);
        serviceImpls      = serviceImpls      == null ? Set.of() : Set.copyOf(serviceImpls);
        autoConfigClasses = autoConfigClasses  == null ? Set.of() : Set.copyOf(autoConfigClasses);
    }
}
