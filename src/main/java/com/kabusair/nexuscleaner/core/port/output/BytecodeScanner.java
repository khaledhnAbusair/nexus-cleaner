package com.kabusair.nexuscleaner.core.port.output;

import com.kabusair.nexuscleaner.core.domain.model.SymbolReference;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Deep-scans compiled {@code .class} files and extracts every referenced symbol,
 * including reflection hints (string constants passed to {@code Class.forName} etc.).
 */
public interface BytecodeScanner {

    Set<SymbolReference> scan(List<Path> classRoots);
}
