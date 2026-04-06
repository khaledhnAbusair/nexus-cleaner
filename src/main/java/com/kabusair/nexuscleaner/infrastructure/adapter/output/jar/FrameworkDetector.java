package com.kabusair.nexuscleaner.infrastructure.adapter.output.jar;

import com.kabusair.nexuscleaner.core.domain.model.DependencyCoordinate;

import java.util.Set;
import java.util.jar.JarFile;

/**
 * Identifies meta-dependencies (starters, BOMs, framework wiring JARs) that
 * exist solely to pull in a curated set of transitives. These JARs often
 * contain zero user-facing classes — flagging them as UNUSED is misleading
 * because their purpose is dependency aggregation, not direct code usage.
 *
 * <p>Detection uses two signals:
 * <ol>
 *   <li>GAV pattern matching for well-known starter conventions</li>
 *   <li>Structural analysis: JARs with zero exported classes but non-empty
 *       POM dependency lists are likely meta-dependencies</li>
 * </ol>
 */
public final class FrameworkDetector {

    private static final Set<String> STARTER_GROUP_PREFIXES = Set.of(
            "org.springframework.boot",
            "org.springframework.cloud",
            "io.quarkus",
            "io.micronaut",
            "io.helidon"
    );

    private static final Set<String> STARTER_ARTIFACT_PATTERNS = Set.of(
            "starter",
            "spring-boot-starter",
            "quarkus-bom",
            "micronaut-bom"
    );

    private static final Set<String> FRAMEWORK_RUNTIME_GROUPS = Set.of(
            "org.springframework",
            "org.springframework.boot",
            "org.springframework.security",
            "org.springframework.data",
            "org.hibernate.orm",
            "org.hibernate.validator",
            "ch.qos.logback",
            "org.slf4j",
            "org.apache.logging.log4j",
            "org.ehcache",
            "javax.cache",
            "org.apache.tomcat.embed",
            "org.eclipse.jetty",
            "io.netty",
            "io.micrometer",
            "org.aspectj",
            "com.zaxxer",
            "org.liquibase",
            "org.flywaydb",
            "org.apache.camel",
            "org.apache.activemq",
            "com.fasterxml.jackson.core",
            "com.fasterxml.jackson.datatype",
            "tools.jackson.core"
    );

    public boolean isStarterOrFramework(DependencyCoordinate coord, int exportedClassCount) {
        if (isStarterByConvention(coord)) return true;
        if (isFrameworkRuntime(coord)) return true;
        return isEmptyMetaDependency(coord, exportedClassCount);
    }

    private boolean isStarterByConvention(DependencyCoordinate coord) {
        String artifactId = coord.artifactId();
        for (String pattern : STARTER_ARTIFACT_PATTERNS) {
            if (artifactId.contains(pattern)) return true;
        }
        for (String prefix : STARTER_GROUP_PREFIXES) {
            if (coord.groupId().startsWith(prefix) && artifactId.contains("starter")) return true;
        }
        return false;
    }

    private boolean isFrameworkRuntime(DependencyCoordinate coord) {
        return FRAMEWORK_RUNTIME_GROUPS.contains(coord.groupId());
    }

    private boolean isEmptyMetaDependency(DependencyCoordinate coord, int exportedClassCount) {
        return exportedClassCount == 0;
    }
}
