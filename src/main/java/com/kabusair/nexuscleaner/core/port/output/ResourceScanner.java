package com.kabusair.nexuscleaner.core.port.output;

import com.kabusair.nexuscleaner.core.domain.model.SymbolReference;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Extracts FQCN references from project resource files: application.properties,
 * application.yml, persistence.xml, META-INF/services, META-INF/spring.factories,
 * and META-INF/spring/*.imports.
 */
public interface ResourceScanner {

    Set<SymbolReference> scan(List<Path> resourceRoots);
}
