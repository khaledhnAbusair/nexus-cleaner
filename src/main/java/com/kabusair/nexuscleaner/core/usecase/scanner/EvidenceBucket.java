package com.kabusair.nexuscleaner.core.usecase.scanner;

import com.kabusair.nexuscleaner.core.domain.model.DependencyCoordinate;
import com.kabusair.nexuscleaner.core.domain.model.UsageEvidence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable accumulator owned by {@link UsageMatcher} while it walks scanned
 * symbols. Kept in its own file so the matcher class contains only logic.
 */
final class EvidenceBucket {

    private final Map<DependencyCoordinate, List<UsageEvidence>> byDependency = new HashMap<>();

    void add(UsageEvidence evidence) {
        byDependency
                .computeIfAbsent(evidence.dependency(), k -> new ArrayList<>())
                .add(evidence);
    }

    Map<DependencyCoordinate, List<UsageEvidence>> asMap() {
        return byDependency;
    }
}
