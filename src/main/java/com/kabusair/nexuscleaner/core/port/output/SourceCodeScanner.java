package com.kabusair.nexuscleaner.core.port.output;

import com.kabusair.nexuscleaner.core.domain.model.SymbolReference;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Extracts import statements and annotation uses from {@code .java} sources.
 *
 * <p>Source-level evidence is the primary signal for PROVIDED-scope dependencies
 * (annotation processors, Lombok, etc.) that leave no trace in bytecode.
 */
public interface SourceCodeScanner {

    Set<SymbolReference> scan(List<Path> sourceRoots);
}
