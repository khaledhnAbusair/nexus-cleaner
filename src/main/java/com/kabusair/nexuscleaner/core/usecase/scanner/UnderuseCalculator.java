package com.kabusair.nexuscleaner.core.usecase.scanner;

import com.kabusair.nexuscleaner.core.domain.model.JarIndex;
import com.kabusair.nexuscleaner.core.domain.model.SymbolKind;
import com.kabusair.nexuscleaner.core.domain.model.SymbolReference;
import com.kabusair.nexuscleaner.core.domain.model.UsageEvidence;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides whether a used dependency is underused based on how much of its public
 * API surface is actually referenced. Returns a ratio in [0, 1] that the caller
 * compares against the configured threshold.
 */
public final class UnderuseCalculator {

    public double usageRatio(List<UsageEvidence> evidence, JarIndex index) {
        if (index == null || index.publicApiSize() <= 0) return 1.0d;
        int referenced = countDistinctReferencedClasses(evidence);
        if (referenced == 0) return 0.0d;
        return (double) referenced / (double) index.publicApiSize();
    }

    private int countDistinctReferencedClasses(List<UsageEvidence> evidence) {
        Set<String> distinct = new HashSet<>();
        for (UsageEvidence e : evidence) {
            collectOwnerIfClassLevel(e.symbol(), distinct);
        }
        return distinct.size();
    }

    private void collectOwnerIfClassLevel(SymbolReference symbol, Set<String> out) {
        if (symbol == null || symbol.owner() == null) return;
        if (isClassLevelKind(symbol.kind())) {
            out.add(symbol.owner());
        }
    }

    private boolean isClassLevelKind(SymbolKind kind) {
        return kind == SymbolKind.TYPE_REFERENCE
                || kind == SymbolKind.METHOD_CALL
                || kind == SymbolKind.FIELD_ACCESS
                || kind == SymbolKind.IMPORT
                || kind == SymbolKind.ANNOTATION;
    }
}
