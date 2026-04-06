package com.kabusair.nexuscleaner.infrastructure.adapter.output.source;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Extracts {@code scanBasePackages} values from {@code @SpringBootApplication}
 * and {@code basePackages} from {@code @ComponentScan}. These package prefixes
 * tell Spring Boot to auto-discover any {@code @Component}, {@code @Service},
 * {@code @Configuration}, etc. on the classpath matching those packages.
 *
 * <p>When a dependency's exported classes fall under a scanned package, it is
 * considered auto-wired by Spring and should not be flagged as UNUSED.
 */
public final class SpringComponentScanExtractor {

    private static final Pattern SCAN_BASE_PACKAGES = Pattern.compile(
            "scanBasePackages\\s*=\\s*(?:\"([^\"]+)\"|\\{([^}]+)})");

    private static final Pattern BASE_PACKAGES = Pattern.compile(
            "basePackages\\s*=\\s*(?:\"([^\"]+)\"|\\{([^}]+)})");

    private static final Pattern QUOTED_STRING = Pattern.compile("\"([^\"]+)\"");

    public Set<String> extractScanPackages(List<Path> sourceRoots) {
        Set<String> packages = new HashSet<>();
        for (Path root : sourceRoots) {
            if (!Files.isDirectory(root)) continue;
            scanJavaFiles(root, packages);
        }
        return packages;
    }

    private void scanJavaFiles(Path root, Set<String> packages) {
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .forEach(p -> extractFromFile(p, packages));
        } catch (IOException ignored) {
        }
    }

    private void extractFromFile(Path javaFile, Set<String> packages) {
        try (BufferedReader reader = Files.newBufferedReader(javaFile)) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
            }
            String text = content.toString();
            extractPatterns(text, SCAN_BASE_PACKAGES, packages);
            extractPatterns(text, BASE_PACKAGES, packages);
        } catch (IOException ignored) {
        }
    }

    private void extractPatterns(String text, Pattern pattern, Set<String> packages) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String singleValue = matcher.group(1);
            String arrayValue = matcher.group(2);
            if (singleValue != null) {
                addPackagePrefix(singleValue.trim(), packages);
            }
            if (arrayValue != null) {
                extractQuotedStrings(arrayValue, packages);
            }
        }
    }

    private void extractQuotedStrings(String arrayContent, Set<String> packages) {
        Matcher matcher = QUOTED_STRING.matcher(arrayContent);
        while (matcher.find()) {
            addPackagePrefix(matcher.group(1).trim(), packages);
        }
    }

    private void addPackagePrefix(String value, Set<String> packages) {
        String cleaned = value.replace("*", "").replace("\"", "").trim();
        if (cleaned.endsWith(".")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (!cleaned.isEmpty() && cleaned.contains(".")) {
            packages.add(cleaned);
        }
    }
}
