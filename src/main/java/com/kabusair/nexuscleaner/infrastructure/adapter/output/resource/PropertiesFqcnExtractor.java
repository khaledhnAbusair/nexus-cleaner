package com.kabusair.nexuscleaner.infrastructure.adapter.output.resource;

import com.kabusair.nexuscleaner.core.domain.model.SymbolReference;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Extracts FQCN values from {@code .properties} files. Targets keys like
 * {@code spring.datasource.driver-class-name}, {@code spring.jpa.properties.hibernate.*},
 * and any value that looks like a fully qualified Java class name.
 */
final class PropertiesFqcnExtractor {

    private static final Pattern FQCN_PATTERN =
            Pattern.compile("^[a-zA-Z_$][\\w$]*(\\.[a-zA-Z_$][\\w$]*)+$");

    void extract(Path propertiesFile, Set<SymbolReference> sink) {
        try (BufferedReader reader = Files.newBufferedReader(propertiesFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                processLine(line, sink);
            }
        } catch (IOException ignored) {
        }
    }

    private void processLine(String line, Set<SymbolReference> sink) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) return;
        int sep = findSeparator(trimmed);
        if (sep < 0) return;
        String value = trimmed.substring(sep + 1).trim();
        if (looksLikeFqcn(value)) {
            sink.add(SymbolReference.reflectionHint(value));
        }
    }

    private int findSeparator(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '=' || c == ':') return i;
        }
        return -1;
    }

    private boolean looksLikeFqcn(String s) {
        return s != null && s.length() >= 3 && s.length() < 512 && FQCN_PATTERN.matcher(s).matches();
    }
}
