package com.kabusair.nexuscleaner.core.domain.model;

public record UsageEvidence(
        DependencyCoordinate dependency,
        SymbolReference symbol,
        EvidenceLayer layer
) {

    public boolean isStrong() {
        return switch (layer) {
            case BYTECODE_TYPE, BYTECODE_METHOD, BYTECODE_FIELD,
                 SOURCE_IMPORT, SOURCE_ANNOTATION -> true;
            default -> false;
        };
    }
}
