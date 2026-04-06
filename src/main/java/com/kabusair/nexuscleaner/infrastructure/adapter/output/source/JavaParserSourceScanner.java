package com.kabusair.nexuscleaner.infrastructure.adapter.output.source;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.kabusair.nexuscleaner.core.domain.exception.NexusCleanerException;
import com.kabusair.nexuscleaner.core.domain.model.SymbolReference;
import com.kabusair.nexuscleaner.core.port.output.SourceCodeScanner;
import com.kabusair.nexuscleaner.infrastructure.concurrent.VirtualThreadExecutorFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Stream;

/**
 * JavaParser-backed source scanner. Captures import declarations (as
 * {@link SymbolReference.Kind#IMPORT IMPORT}) and annotation uses (as
 * {@code ANNOTATION}) — the two channels that matter for PROVIDED-scope
 * dependencies such as Lombok or JSR-305 annotation libraries that leave no
 * bytecode trace.
 */
public final class JavaParserSourceScanner implements SourceCodeScanner {

    private static final String JAVA_SUFFIX = ".java";

    private final VirtualThreadExecutorFactory executorFactory;

    public JavaParserSourceScanner(VirtualThreadExecutorFactory executorFactory) {
        this.executorFactory = executorFactory;
    }

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(JavaParserSourceScanner.class.getName());

    @Override
    public Set<SymbolReference> scan(List<Path> sourceRoots) {
        if (sourceRoots == null || sourceRoots.isEmpty()) return Set.of();
        try {
            return doScan(sourceRoots);
        } catch (RuntimeException e) {
            LOG.warning("Source scanner failed (may happen in native-image mode): " + e.getMessage()
                    + " — continuing with bytecode and resource scanning only");
            return Set.of();
        }
    }

    private Set<SymbolReference> doScan(List<Path> sourceRoots) {
        List<Path> sourceFiles = discoverSourceFiles(sourceRoots);
        if (sourceFiles.isEmpty()) return Set.of();
        Set<SymbolReference> collected = ConcurrentHashMap.newKeySet();
        scanInParallel(sourceFiles, collected);
        return Set.copyOf(collected);
    }

    private void scanInParallel(List<Path> sourceFiles, Set<SymbolReference> sink) {
        try (ExecutorService executorService = executorFactory.newPerTaskExecutor()) {
            List<Future<?>> futures = new java.util.ArrayList<>(sourceFiles.size());
            for (Path file : sourceFiles) {
                futures.add(executorService.submit(() -> scanOne(file, sink)));
            }
            awaitAll(futures);
        }
    }

    private List<Path> discoverSourceFiles(List<Path> roots) {
        List<Path> out = new java.util.ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            walkSources(root, out);
        }
        return out;
    }

    private void walkSources(Path root, List<Path> out) {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(JAVA_SUFFIX))
                    .forEach(out::add);
        } catch (IOException e) {
            throw new NexusCleanerException("Failed to walk source root " + root, e);
        }
    }

    private void scanOne(Path javaFile, Set<SymbolReference> sink) {
        CompilationUnit unit = parseUnit(javaFile);
        if (unit == null) return;
        collectImports(unit, sink);
        collectAnnotations(unit, sink);
    }

    private CompilationUnit parseUnit(Path javaFile) {
        try {
            return StaticJavaParser.parse(javaFile);
        } catch (IOException e) {
            throw new NexusCleanerException("Failed to read " + javaFile, e);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void collectImports(CompilationUnit unit, Set<SymbolReference> sink) {
        for (ImportDeclaration imp : unit.getImports()) {
            String name = imp.getNameAsString();
            if (!name.isEmpty()) {
                sink.add(SymbolReference.importRef(name));
            }
        }
    }

    private void collectAnnotations(CompilationUnit unit, Set<SymbolReference> sink) {
        Set<String> seen = new HashSet<>();
        unit.findAll(AnnotationExpr.class)
                .forEach(anno -> recordAnnotation(anno, unit, sink, seen));
    }

    private void recordAnnotation(AnnotationExpr anno,
                                  CompilationUnit unit,
                                  Set<SymbolReference> sink,
                                  Set<String> seen) {
        String fqcn = resolveAnnotationFqcn(anno, unit);
        if (!seen.add(fqcn)) return;
        sink.add(SymbolReference.annotation(fqcn));
    }

    private String resolveAnnotationFqcn(AnnotationExpr anno, CompilationUnit unit) {
        String simple = anno.getNameAsString();
        if (simple.contains(".")) return simple;
        for (ImportDeclaration imp : unit.getImports()) {
            String importName = imp.getNameAsString();
            if (importName.endsWith("." + simple)) return importName;
        }
        return simple;
    }

    private void awaitAll(List<Future<?>> futures) {
        for (Future<?> f : futures) {
            awaitOne(f);
        }
    }

    private void awaitOne(Future<?> f) {
        try {
            f.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NexusCleanerException("Source scan interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NexusCleanerException nce) throw nce;
            throw new NexusCleanerException("Source scan task failed", cause);
        }
    }
}
