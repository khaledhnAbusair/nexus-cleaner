package com.kabusair.nexuscleaner.core.usecase.scanner;

import com.kabusair.nexuscleaner.core.domain.model.DependencyCoordinate;
import com.kabusair.nexuscleaner.core.domain.model.JarIndex;
import com.kabusair.nexuscleaner.core.domain.model.SymbolReference;

import java.util.Map;
import java.util.Set;

/**
 * Immutable bundle of everything the audit pipeline needs. Includes source,
 * bytecode, resource-level symbols, Spring Boot detection flag, and the set
 * of package prefixes from {@code @SpringBootApplication(scanBasePackages)}
 * and {@code @ComponentScan(basePackages)}.
 */
public record ScanContext(
        Set<SymbolReference> mainSourceSymbols,
        Set<SymbolReference> testSourceSymbols,
        Set<SymbolReference> mainBytecodeSymbols,
        Set<SymbolReference> testBytecodeSymbols,
        Set<SymbolReference> resourceSymbols,
        Map<DependencyCoordinate, JarIndex> indicesByDependency,
        boolean springBootDetected,
        Set<String> componentScanPackages
) {
    public ScanContext {
        mainSourceSymbols    = nullToEmpty(mainSourceSymbols);
        testSourceSymbols    = nullToEmpty(testSourceSymbols);
        mainBytecodeSymbols  = nullToEmpty(mainBytecodeSymbols);
        testBytecodeSymbols  = nullToEmpty(testBytecodeSymbols);
        resourceSymbols      = nullToEmpty(resourceSymbols);
        indicesByDependency  = indicesByDependency == null ? Map.of() : Map.copyOf(indicesByDependency);
        componentScanPackages = componentScanPackages == null ? Set.of() : Set.copyOf(componentScanPackages);
    }

    private static Set<SymbolReference> nullToEmpty(Set<SymbolReference> set) {
        return set == null ? Set.of() : Set.copyOf(set);
    }
}
