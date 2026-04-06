package com.kabusair.nexuscleaner.infrastructure.adapter.output.repository;

import com.kabusair.nexuscleaner.core.domain.model.DependencyCoordinate;
import com.kabusair.nexuscleaner.core.domain.model.ProjectContext;
import com.kabusair.nexuscleaner.core.port.output.LocalRepositoryLocator;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Chains multiple {@link LocalRepositoryLocator} implementations. Tries each in
 * order and returns the first hit. If all miss, logs at FINE level and returns
 * empty — the audit continues with reduced accuracy rather than failing.
 */
public final class CompositeLocator implements LocalRepositoryLocator {

    private static final Logger LOG = Logger.getLogger(CompositeLocator.class.getName());

    private final List<LocalRepositoryLocator> delegates;

    public CompositeLocator(List<LocalRepositoryLocator> delegates) {
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public void initialize(ProjectContext project) {
        for (LocalRepositoryLocator delegate : delegates) {
            delegate.initialize(project);
        }
    }

    @Override
    public Optional<Path> locate(DependencyCoordinate coordinate) {
        for (LocalRepositoryLocator delegate : delegates) {
            Optional<Path> result = delegate.locate(coordinate);
            if (result.isPresent()) return result;
        }
        LOG.fine("JAR not found for " + coordinate.gav() + " — dependency will be skipped");
        return Optional.empty();
    }
}
