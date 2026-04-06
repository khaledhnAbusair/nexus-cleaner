package com.kabusair.nexuscleaner.infrastructure.adapter.output.repository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the output of {@code mvn dependency:build-classpath} into a list of
 * absolute JAR paths. Handles both single-line (colon/semicolon separated) and
 * multi-line formats. Extracts artifactId and version from each filename using
 * the standard Maven naming convention: {@code artifactId-version[-classifier].jar}.
 */
final class ClasspathOutputParser {

    private static final Pattern JAR_FILENAME_PATTERN =
            Pattern.compile("^(.+?)-(\\d[\\w.\\-]*)(?:-[\\w]+)?\\.jar$");

    List<ClasspathEntry> parse(List<String> lines) {
        List<ClasspathEntry> entries = new ArrayList<>();
        for (String line : lines) {
            parseLine(line, entries);
        }
        return entries;
    }

    private void parseLine(String line, List<ClasspathEntry> entries) {
        String trimmed = stripMavenPrefix(line).trim();
        if (trimmed.isEmpty()) return;
        if (looksLikeMavenLog(trimmed)) return;

        String separator = trimmed.contains(";") ? ";" : ":";
        for (String segment : trimmed.split(separator)) {
            parseSegment(segment.trim(), entries);
        }
    }

    private void parseSegment(String segment, List<ClasspathEntry> entries) {
        if (segment.isEmpty()) return;
        Path path = Path.of(segment);
        String fileName = path.getFileName().toString();
        if (!fileName.endsWith(".jar")) return;

        Matcher m = JAR_FILENAME_PATTERN.matcher(fileName);
        if (!m.matches()) return;

        entries.add(new ClasspathEntry(m.group(1), m.group(2), path));
    }

    private String stripMavenPrefix(String line) {
        if (line.startsWith("[INFO] ")) return line.substring(7);
        if (line.startsWith("[WARNING] ") || line.startsWith("[ERROR] ")) return "";
        return line;
    }

    private boolean looksLikeMavenLog(String line) {
        return line.startsWith("---")
                || line.startsWith("BUILD")
                || line.startsWith("Scanning")
                || line.startsWith("Dependencies classpath")
                || line.startsWith("Downloaded")
                || line.startsWith("Downloading")
                || line.contains("--------");
    }
}
