package com.kabusair.nexuscleaner.core.domain.model;

import java.util.Objects;

/**
 * A package relocation produced by shading tools. When a library is shaded into
 * another JAR its classes are moved under a prefix (e.g. {@code com.acme.shaded.guava.*});
 * code written against the original {@code com.google.common.*} still works, but a
 * naive index would not match it. {@link #relocate(String)} rewrites an FQCN written
 * against the original package into the actually-present shaded package so the
 * matcher can resolve the reference against this JAR.
 */
public record RelocationRule(String fromPackage, String toPackage) {

    public RelocationRule {
        Objects.requireNonNull(fromPackage, "fromPackage");
        Objects.requireNonNull(toPackage, "toPackage");
    }

    public String relocate(String fqcn) {
        if (fqcn == null) return null;
        if (fqcn.equals(fromPackage) || fqcn.startsWith(fromPackage + ".")) {
            return toPackage + fqcn.substring(fromPackage.length());
        }
        return fqcn;
    }
}
