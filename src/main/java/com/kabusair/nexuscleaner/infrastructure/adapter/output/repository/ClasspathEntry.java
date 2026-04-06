package com.kabusair.nexuscleaner.infrastructure.adapter.output.repository;

import java.nio.file.Path;

record ClasspathEntry(String artifactId, String version, Path absolutePath) {

    String fileKey() {
        return artifactId + "-" + version;
    }
}
