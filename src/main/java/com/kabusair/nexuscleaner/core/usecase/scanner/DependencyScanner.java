package com.kabusair.nexuscleaner.core.usecase.scanner;

import com.kabusair.nexuscleaner.core.domain.model.Dependency;
import com.kabusair.nexuscleaner.core.domain.model.DependencyCoordinate;
import com.kabusair.nexuscleaner.core.domain.model.DependencyGraph;
import com.kabusair.nexuscleaner.core.domain.model.JarIndex;
import com.kabusair.nexuscleaner.core.domain.model.ProjectContext;
import com.kabusair.nexuscleaner.core.domain.model.SymbolKind;
import com.kabusair.nexuscleaner.core.domain.model.SymbolReference;
import com.kabusair.nexuscleaner.core.port.output.BytecodeScanner;
import com.kabusair.nexuscleaner.core.port.output.JarIndexer;
import com.kabusair.nexuscleaner.core.port.output.LocalRepositoryLocator;
import com.kabusair.nexuscleaner.core.port.output.ResourceScanner;
import com.kabusair.nexuscleaner.core.port.output.SourceCodeScanner;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.source.SpringComponentScanExtractor;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Orchestrates the four scan phases — source, bytecode, resources, JAR indexing —
 * and bundles the results into a {@link ScanContext}. Also detects
 * {@code @SpringBootApplication} and extracts {@code scanBasePackages} /
 * {@code @ComponentScan(basePackages)} for auto-promotion of scanned deps.
 */
public final class DependencyScanner {

    private static final String SPRING_BOOT_APP_ANNOTATION =
            "org.springframework.boot.autoconfigure.SpringBootApplication";

    private final SourceCodeScanner sourceScanner;
    private final BytecodeScanner bytecodeScanner;
    private final ResourceScanner resourceScanner;
    private final JarIndexer jarIndexer;
    private final LocalRepositoryLocator repositoryLocator;
    private final SpringComponentScanExtractor componentScanExtractor;

    public DependencyScanner(SourceCodeScanner sourceScanner,
                             BytecodeScanner bytecodeScanner,
                             ResourceScanner resourceScanner,
                             JarIndexer jarIndexer,
                             LocalRepositoryLocator repositoryLocator,
                             SpringComponentScanExtractor componentScanExtractor) {
        this.sourceScanner = sourceScanner;
        this.bytecodeScanner = bytecodeScanner;
        this.resourceScanner = resourceScanner;
        this.jarIndexer = jarIndexer;
        this.repositoryLocator = repositoryLocator;
        this.componentScanExtractor = componentScanExtractor;
    }

    public ScanContext scan(ProjectContext project, DependencyGraph graph) {
        repositoryLocator.initialize(project);
        Set<SymbolReference> mainSrc = sourceScanner.scan(project.mainSourceRoots());
        Set<SymbolReference> testSrc = sourceScanner.scan(project.testSourceRoots());
        Set<SymbolReference> mainBc  = bytecodeScanner.scan(project.mainClassRoots());
        Set<SymbolReference> testBc  = bytecodeScanner.scan(project.testClassRoots());
        Set<SymbolReference> resSym  = resourceScanner.scan(project.mainResourceRoots());
        Map<DependencyCoordinate, JarIndex> indices = indexAllJars(graph);
        boolean springBoot = detectSpringBootApplication(mainSrc, mainBc);
        Set<String> scanPackages = componentScanExtractor.extractScanPackages(project.mainSourceRoots());
        return new ScanContext(mainSrc, testSrc, mainBc, testBc, resSym, indices, springBoot, scanPackages);
    }

    private boolean detectSpringBootApplication(Set<SymbolReference> sourceSym,
                                                Set<SymbolReference> bytecodeSym) {
        return containsAnnotation(sourceSym) || containsAnnotation(bytecodeSym);
    }

    private boolean containsAnnotation(Set<SymbolReference> symbols) {
        for (SymbolReference sym : symbols) {
            if (sym.kind() == SymbolKind.ANNOTATION
                    && SPRING_BOOT_APP_ANNOTATION.equals(sym.owner())) {
                return true;
            }
            if (sym.kind() == SymbolKind.IMPORT
                    && SPRING_BOOT_APP_ANNOTATION.equals(sym.owner())) {
                return true;
            }
        }
        return false;
    }

    private Map<DependencyCoordinate, JarIndex> indexAllJars(DependencyGraph graph) {
        Map<DependencyCoordinate, JarIndex> out = new HashMap<>();
        for (Dependency dependency : graph.allByGa().values()) {
            indexOne(dependency, out);
        }
        return out;
    }

    private void indexOne(Dependency dependency, Map<DependencyCoordinate, JarIndex> out) {
        Optional<Path> jar = repositoryLocator.locate(dependency.coordinate());
        if (jar.isEmpty()) return;
        JarIndex index = jarIndexer.index(jar.get(), dependency.coordinate());
        out.put(dependency.coordinate(), index);
    }
}
