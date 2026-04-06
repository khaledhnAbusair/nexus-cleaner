package com.kabusair.nexuscleaner.infrastructure.adapter.output.resource;

import com.kabusair.nexuscleaner.core.domain.model.SymbolReference;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans all {@code .java} and {@code .xml} files for string literals that
 * contain FQCNs. This catches patterns invisible to both bytecode and source
 * import analysis:
 * <ul>
 *   <li>Jupiter Framework registry: {@code "com.progressoft.jupiter.business.MyHandler"}</li>
 *   <li>Spring XML beans: {@code class="com.example.Foo"}</li>
 *   <li>Dynamic class loading with string concatenation or constants</li>
 *   <li>Configuration keys that hold class names</li>
 * </ul>
 *
 * <p>The scanner searches for any quoted string that matches the FQCN pattern
 * ({@code package.Class}) regardless of whether it's an import, annotation,
 * or free-standing string literal. Every hit is emitted as a
 * {@code REFLECTION_HINT} so the matcher can resolve it against JarIndex.
 */
final class GlobalFqcnStringExtractor {

    private static final Pattern FQCN_IN_STRING = Pattern.compile(
            "\"([a-zA-Z_$][\\w$]*(\\.[a-zA-Z_$][\\w$]*){2,})\"");

    void extract(Path file, Set<SymbolReference> sink) {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                extractFromLine(line, sink);
            }
        } catch (IOException ignored) {
        }
    }

    private void extractFromLine(String line, Set<SymbolReference> sink) {
        Matcher matcher = FQCN_IN_STRING.matcher(line);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (isValidFqcn(candidate)) {
                sink.add(SymbolReference.reflectionHint(candidate));
            }
        }
    }

    private boolean isValidFqcn(String s) {
        if (s.length() < 5 || s.length() > 512) return false;
        if (s.startsWith("java.") || s.startsWith("javax.") || s.startsWith("sun.")) return false;
        if (s.endsWith(".class") || s.endsWith(".java") || s.endsWith(".xml")) return false;
        if (s.contains("//") || s.contains("http") || s.contains("www.")) return false;
        return true;
    }
}
