package com.kabusair.nexuscleaner.core.port.output;

import com.kabusair.nexuscleaner.core.domain.model.DependencyCoordinate;
import com.kabusair.nexuscleaner.core.domain.model.JarIndex;

import java.nio.file.Path;

public interface JarIndexer {

    JarIndex index(Path jarFile, DependencyCoordinate coordinate);
}
