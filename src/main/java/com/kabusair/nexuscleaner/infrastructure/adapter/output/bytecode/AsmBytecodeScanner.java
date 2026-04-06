package com.kabusair.nexuscleaner.infrastructure.adapter.output.bytecode;

import com.kabusair.nexuscleaner.core.domain.exception.NexusCleanerException;
import com.kabusair.nexuscleaner.core.domain.model.SymbolReference;
import com.kabusair.nexuscleaner.core.port.output.BytecodeScanner;
import com.kabusair.nexuscleaner.infrastructure.concurrent.VirtualThreadExecutorFactory;
import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Stream;

/**
 * ASM-based implementation of {@link BytecodeScanner}. Streams every {@code .class}
 * file via the visitor API (not the tree API) and fans work out across virtual
 * threads — the per-class workload is I/O bound, so one virtual thread per file
 * is optimal and never exhausts platform threads.
 */
public final class AsmBytecodeScanner implements BytecodeScanner {

    private static final String CLASS_SUFFIX = ".class";
    private static final String MODULE_INFO = "module-info.class";
    private static final int READER_FLAGS = ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES;

    private final VirtualThreadExecutorFactory executorFactory;

    public AsmBytecodeScanner(VirtualThreadExecutorFactory executorFactory) {
        this.executorFactory = executorFactory;
    }

    @Override
    public Set<SymbolReference> scan(List<Path> classRoots) {
        if (classRoots == null || classRoots.isEmpty()) return Set.of();

        List<Path> classFiles = discoverClassFiles(classRoots);
        if (classFiles.isEmpty()) return Set.of();

        Set<SymbolReference> collected = ConcurrentHashMap.newKeySet();
        runScan(classFiles, collected);
        return Collections.unmodifiableSet(collected);
    }

    private void runScan(List<Path> classFiles, Set<SymbolReference> collected) {
        try (ExecutorService executorService = executorFactory.newPerTaskExecutor()) {
            List<Future<?>> futures = submitAll(executorService, classFiles, collected);
            awaitAll(futures);
        }
    }

    private List<Future<?>> submitAll(ExecutorService exec, List<Path> classFiles, Set<SymbolReference> sink) {
        List<Future<?>> futures = new ArrayList<>(classFiles.size());
        for (Path classFile : classFiles) {
            futures.add(exec.submit(() -> scanOne(classFile, sink)));
        }
        return futures;
    }

    private List<Path> discoverClassFiles(List<Path> roots) {
        List<Path> out = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            collectClassesUnder(root, out);
        }
        return out;
    }

    private void collectClassesUnder(Path root, List<Path> out) {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(this::isScannableClassFile)
                    .forEach(out::add);
        } catch (IOException e) {
            throw new NexusCleanerException("Failed to walk class-file root " + root, e);
        }
    }

    private boolean isScannableClassFile(Path p) {
        String name = p.getFileName().toString();
        return name.endsWith(CLASS_SUFFIX) && !name.equals(MODULE_INFO);
    }

    private void scanOne(Path classFile, Set<SymbolReference> sink) {
        try {
            byte[] bytes = Files.readAllBytes(classFile);
            new ClassReader(bytes).accept(new SymbolCollectingClassVisitor(sink), READER_FLAGS);
        } catch (IOException e) {
            throw new NexusCleanerException("Failed to read class file " + classFile, e);
        }
    }

    private void awaitAll(List<Future<?>> futures) {
        futures.forEach(this::awaitOne);
    }

    private void awaitOne(Future<?> f) {
        try {
            f.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NexusCleanerException("Bytecode scan interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NexusCleanerException nce) throw nce;
            throw new NexusCleanerException("Bytecode scan task failed", cause);
        }
    }
}
