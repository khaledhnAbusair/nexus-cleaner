package com.kabusair.nexuscleaner.infrastructure.adapter.output.resource;

import com.kabusair.nexuscleaner.core.domain.model.SymbolReference;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Extracts implementation FQCNs from project-side {@code META-INF/services/*}
 * files. Each non-blank, non-comment line is a FQCN that should be treated
 * as a usage of the dependency that declares the corresponding SPI interface.
 */
final class ServiceFileExtractor {

    void extract(Path serviceFile, Set<SymbolReference> sink) {
        try (BufferedReader reader = Files.newBufferedReader(serviceFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                processLine(line, sink);
            }
        } catch (IOException ignored) {
        }
    }

    private void processLine(String line, Set<SymbolReference> sink) {
        String trimmed = stripComment(line).trim();
        if (trimmed.isEmpty()) return;
        sink.add(SymbolReference.reflectionHint(trimmed));
    }

    private String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash >= 0 ? line.substring(0, hash) : line;
    }
}
