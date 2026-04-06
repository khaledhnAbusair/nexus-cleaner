package com.kabusair.nexuscleaner.infrastructure.adapter.output.resource;

import com.kabusair.nexuscleaner.core.domain.model.SymbolReference;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lightweight FQCN extractor for YAML files. Performs line-by-line scanning
 * without a full YAML parser to stay dependency-free and GraalVM-friendly.
 * Targets values that match FQCN patterns in key-value lines.
 */
final class YamlFqcnExtractor {

    private static final Pattern FQCN_PATTERN =
            Pattern.compile("^[a-zA-Z_$][\\w$]*(\\.[a-zA-Z_$][\\w$]*)+$");

    void extract(Path yamlFile, Set<SymbolReference> sink) {
        try (BufferedReader reader = Files.newBufferedReader(yamlFile)) {
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
        int colon = trimmed.indexOf(':');
        if (colon < 0 || colon == trimmed.length() - 1) return;
        String value = trimmed.substring(colon + 1).trim();
        value = stripQuotes(value);
        if (looksLikeFqcn(value)) {
            sink.add(SymbolReference.reflectionHint(value));
        }
    }

    private String stripQuotes(String s) {
        if (s.length() >= 2) {
            char first = s.charAt(0);
            char last = s.charAt(s.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return s.substring(1, s.length() - 1);
            }
        }
        return s;
    }

    private boolean looksLikeFqcn(String s) {
        return s != null && s.length() >= 3 && s.length() < 512 && FQCN_PATTERN.matcher(s).matches();
    }
}
