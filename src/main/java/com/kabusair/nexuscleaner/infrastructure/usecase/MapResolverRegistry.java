package com.kabusair.nexuscleaner.infrastructure.usecase;

import com.kabusair.nexuscleaner.core.domain.model.BuildSystem;
import com.kabusair.nexuscleaner.core.port.output.DependencyGraphResolver;
import com.kabusair.nexuscleaner.core.usecase.ResolverRegistry;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class MapResolverRegistry implements ResolverRegistry {

    private final Map<BuildSystem, DependencyGraphResolver> byBuildSystem;

    public MapResolverRegistry(List<DependencyGraphResolver> resolvers) {
        this.byBuildSystem = indexByBuildSystem(resolvers);
    }

    @Override
    public DependencyGraphResolver resolverFor(BuildSystem buildSystem) {
        return byBuildSystem.get(buildSystem);
    }

    private Map<BuildSystem, DependencyGraphResolver> indexByBuildSystem(List<DependencyGraphResolver> resolvers) {
        Map<BuildSystem, DependencyGraphResolver> map = new EnumMap<>(BuildSystem.class);
        for (DependencyGraphResolver resolver : resolvers) {
            map.put(resolver.supports(), resolver);
        }
        return map;
    }
}
