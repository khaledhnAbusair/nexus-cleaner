package com.kabusair.nexuscleaner.core.usecase.scanner;

import com.kabusair.nexuscleaner.core.domain.model.DependencyCoordinate;
import com.kabusair.nexuscleaner.core.domain.model.EvidenceLayer;
import com.kabusair.nexuscleaner.core.domain.model.JarIndex;
import com.kabusair.nexuscleaner.core.domain.model.SymbolKind;
import com.kabusair.nexuscleaner.core.domain.model.SymbolReference;
import com.kabusair.nexuscleaner.core.domain.model.UsageEvidence;

import java.util.List;
import java.util.Map;

/**
 * Maps every scanned {@link SymbolReference} to the dependency (if any) that
 * provides it. Emits one {@link UsageEvidence} per hit; downstream services
 * aggregate these to determine dependency health. Only contains matching logic —
 * all data carriers live in their own files.
 */
public final class UsageMatcher {

    private final JarIndexMatcher jarMatcher;

    public UsageMatcher(JarIndexMatcher jarMatcher) {
        this.jarMatcher = jarMatcher;
    }

    public Map<DependencyCoordinate, List<UsageEvidence>> match(ScanContext context) {
        EvidenceBucket bucket = new EvidenceBucket();
        matchAllSources(bucket, context);
        matchAllBytecode(bucket, context);
        matchResources(bucket, context);
        return bucket.asMap();
    }

    private void matchAllSources(EvidenceBucket bucket, ScanContext ctx) {
        matchSourceSet(bucket, ctx.mainSourceSymbols(), ctx.indicesByDependency());
        matchSourceSet(bucket, ctx.testSourceSymbols(), ctx.indicesByDependency());
    }

    private void matchAllBytecode(EvidenceBucket bucket, ScanContext ctx) {
        matchBytecodeSet(bucket, ctx.mainBytecodeSymbols(), ctx.indicesByDependency());
        matchBytecodeSet(bucket, ctx.testBytecodeSymbols(), ctx.indicesByDependency());
    }

    private void matchSourceSet(EvidenceBucket bucket,
                                java.util.Set<SymbolReference> symbols,
                                Map<DependencyCoordinate, JarIndex> indices) {
        for (SymbolReference symbol : symbols) {
            matchSourceSymbol(bucket, symbol, indices);
        }
    }

    private void matchBytecodeSet(EvidenceBucket bucket,
                                  java.util.Set<SymbolReference> symbols,
                                  Map<DependencyCoordinate, JarIndex> indices) {
        for (SymbolReference symbol : symbols) {
            matchBytecodeSymbol(bucket, symbol, indices);
        }
    }

    private void matchSourceSymbol(EvidenceBucket bucket,
                                   SymbolReference symbol,
                                   Map<DependencyCoordinate, JarIndex> indices) {
        EvidenceLayer layer = sourceLayerFor(symbol.kind());
        if (layer == null) return;
        emitMatches(bucket, symbol, layer, indices);
    }

    private void matchBytecodeSymbol(EvidenceBucket bucket,
                                     SymbolReference symbol,
                                     Map<DependencyCoordinate, JarIndex> indices) {
        EvidenceLayer layer = bytecodeLayerFor(symbol.kind());
        if (layer == null) return;
        emitMatches(bucket, symbol, layer, indices);
    }

    private void emitMatches(EvidenceBucket bucket,
                             SymbolReference symbol,
                             EvidenceLayer layer,
                             Map<DependencyCoordinate, JarIndex> indices) {
        for (Map.Entry<DependencyCoordinate, JarIndex> entry : indices.entrySet()) {
            if (matchesIndex(symbol, entry.getValue())) {
                bucket.add(new UsageEvidence(entry.getKey(), symbol, layer));
            }
        }
    }

    private boolean matchesIndex(SymbolReference symbol, JarIndex index) {
        return jarMatcher.contains(index, symbol.owner());
    }

    private EvidenceLayer sourceLayerFor(SymbolKind kind) {
        return switch (kind) {
            case IMPORT -> EvidenceLayer.SOURCE_IMPORT;
            case ANNOTATION -> EvidenceLayer.SOURCE_ANNOTATION;
            default -> null;
        };
    }

    private void matchResources(EvidenceBucket bucket, ScanContext ctx) {
        Map<DependencyCoordinate, JarIndex> indices = ctx.indicesByDependency();
        for (SymbolReference symbol : ctx.resourceSymbols()) {
            EvidenceLayer layer = resourceLayerFor(symbol.kind());
            if (layer == null) continue;
            emitMatches(bucket, symbol, layer, indices);
        }
    }

    private EvidenceLayer resourceLayerFor(SymbolKind kind) {
        return switch (kind) {
            case REFLECTION_HINT -> EvidenceLayer.BYTECODE_REFLECTION_HINT;
            case TYPE_REFERENCE -> EvidenceLayer.RESOURCE;
            case IMPORT -> EvidenceLayer.RESOURCE;
            default -> EvidenceLayer.RESOURCE;
        };
    }

    private EvidenceLayer bytecodeLayerFor(SymbolKind kind) {
        return switch (kind) {
            case TYPE_REFERENCE -> EvidenceLayer.BYTECODE_TYPE;
            case METHOD_CALL -> EvidenceLayer.BYTECODE_METHOD;
            case FIELD_ACCESS -> EvidenceLayer.BYTECODE_FIELD;
            case ANNOTATION -> EvidenceLayer.SOURCE_ANNOTATION;
            case REFLECTION_HINT -> EvidenceLayer.BYTECODE_REFLECTION_HINT;
            default -> null;
        };
    }
}
