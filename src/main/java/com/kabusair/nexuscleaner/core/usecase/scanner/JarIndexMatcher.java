package com.kabusair.nexuscleaner.core.usecase.scanner;

import com.kabusair.nexuscleaner.core.domain.model.JarIndex;
import com.kabusair.nexuscleaner.core.domain.model.RelocationRule;

/**
 * Pure-logic lookup service that answers "does this JAR provide the given FQCN?"
 * Consults, in order: exported classes, SPI impls, auto-config manifests
 * (spring.factories / spring/*.imports), and relocation rules.
 */
public final class JarIndexMatcher {

    public boolean contains(JarIndex index, String fqcn) {
        if (index == null || fqcn == null || fqcn.isEmpty()) return false;
        if (index.exportedClasses().contains(fqcn)) return true;
        if (index.serviceImpls().contains(fqcn)) return true;
        if (index.autoConfigClasses().contains(fqcn)) return true;
        return matchesViaRelocation(index, fqcn);
    }

    private boolean matchesViaRelocation(JarIndex index, String fqcn) {
        for (RelocationRule rule : index.relocations()) {
            String relocated = rule.relocate(fqcn);
            if (index.exportedClasses().contains(relocated)) return true;
        }
        return false;
    }
}
