package com.kabusair.nexuscleaner.infrastructure.adapter.output.resource;

import com.kabusair.nexuscleaner.core.domain.model.SymbolReference;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Extracts FQCNs from {@code META-INF/spring.factories} and
 * {@code META-INF/spring/*.imports} files. Every non-blank, non-comment
 * line that contains a FQCN (either as key or value) is emitted as a
 * {@link SymbolReference#reflectionHint(String)}.
 */
final class SpringFactoriesExtractor {

    void extract(Path file, Set<SymbolReference> sink) {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                processLine(line, sink);
            }
        } catch (IOException ignored) {
        }
    }

    private void processLine(String line, Set<SymbolReference> sink) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return;

        if (trimmed.contains("=")) {
            processFactoriesLine(trimmed, sink);
        } else {
            addIfFqcn(trimmed, sink);
        }
    }

    private void processFactoriesLine(String line, Set<SymbolReference> sink) {
        int eq = line.indexOf('=');
        addIfFqcn(line.substring(0, eq).trim(), sink);
        String valuesPart = line.substring(eq + 1).trim();
        if (valuesPart.endsWith("\\")) {
            valuesPart = valuesPart.substring(0, valuesPart.length() - 1).trim();
        }
        for (String fqcn : valuesPart.split(",")) {
            addIfFqcn(fqcn.trim(), sink);
        }
    }

    private void addIfFqcn(String candidate, Set<SymbolReference> sink) {
        if (candidate == null || candidate.isEmpty()) return;
        if (candidate.contains(".") && !candidate.contains(" ") && candidate.length() < 512) {
            sink.add(SymbolReference.reflectionHint(candidate));
        }
    }
}
