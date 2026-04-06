package com.kabusair.nexuscleaner.core.domain.model;

public record SymbolReference(SymbolKind kind, String owner, String member) {

    public static SymbolReference type(String owner) {
        return new SymbolReference(SymbolKind.TYPE_REFERENCE, owner, null);
    }

    public static SymbolReference method(String owner, String name) {
        return new SymbolReference(SymbolKind.METHOD_CALL, owner, name);
    }

    public static SymbolReference field(String owner, String name) {
        return new SymbolReference(SymbolKind.FIELD_ACCESS, owner, name);
    }

    public static SymbolReference annotation(String owner) {
        return new SymbolReference(SymbolKind.ANNOTATION, owner, null);
    }

    public static SymbolReference reflectionHint(String fqcnLiteral) {
        return new SymbolReference(SymbolKind.REFLECTION_HINT, fqcnLiteral, null);
    }

    public static SymbolReference importRef(String owner) {
        return new SymbolReference(SymbolKind.IMPORT, owner, null);
    }
}
