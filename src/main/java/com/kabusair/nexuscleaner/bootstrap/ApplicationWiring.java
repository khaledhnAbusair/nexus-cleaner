package com.kabusair.nexuscleaner.bootstrap;

import com.kabusair.nexuscleaner.core.port.input.AuditProjectUseCase;
import com.kabusair.nexuscleaner.core.port.output.DependencyGraphResolver;
import com.kabusair.nexuscleaner.core.port.output.LocalRepositoryLocator;
import com.kabusair.nexuscleaner.core.usecase.AuditProjectService;
import com.kabusair.nexuscleaner.core.usecase.ProjectContextFactory;
import com.kabusair.nexuscleaner.core.usecase.ResolverRegistry;
import com.kabusair.nexuscleaner.core.usecase.scanner.DependencyScanner;
import com.kabusair.nexuscleaner.core.usecase.scanner.JarIndexMatcher;
import com.kabusair.nexuscleaner.core.usecase.scanner.UnderuseCalculator;
import com.kabusair.nexuscleaner.core.usecase.scanner.UsageMatcher;
import com.kabusair.nexuscleaner.core.usecase.version.VersionChecker;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.ReportWriterResolver;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.build.BuildSystemDetector;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.build.gradle.GradleModuleDetector;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.build.maven.MavenDotTreeParser;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.build.maven.MavenGraphResolver;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.build.maven.PomGavExtractor;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.build.maven.PomModuleDetector;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.build.maven.PomXmlParser;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.bytecode.AsmBytecodeScanner;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.jar.FrameworkDetector;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.jar.JarManifestScanner;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.jar.LocalJarIndexer;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.jar.RelocationDetector;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.jar.ServiceLoaderManifestReader;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.registry.MavenCentralVersionRegistry;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.repository.CompositeLocator;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.repository.InProjectModuleLinker;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.repository.MavenClasspathResolver;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.repository.MavenLocalRepositoryLocator;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.resource.CompositeResourceScanner;
import com.kabusair.nexuscleaner.infrastructure.adapter.output.source.JavaParserSourceScanner;
import com.kabusair.nexuscleaner.infrastructure.concurrent.VirtualThreadExecutorFactory;
import com.kabusair.nexuscleaner.infrastructure.usecase.DefaultProjectContextFactory;
import com.kabusair.nexuscleaner.infrastructure.usecase.MapResolverRegistry;

import java.util.List;

public final class ApplicationWiring {

    private final AuditProjectUseCase auditUseCase;
    private final ReportWriterResolver reportWriterResolver;

    public ApplicationWiring() {
        PomXmlParser pomParser = new PomXmlParser();
        MavenDotTreeParser treeParser = new MavenDotTreeParser();
        DependencyGraphResolver mavenResolver = new MavenGraphResolver(pomParser, treeParser);
        ResolverRegistry resolverRegistry = new MapResolverRegistry(List.of(mavenResolver));

        BuildSystemDetector buildDetector = new BuildSystemDetector();
        PomModuleDetector pomModuleDetector = new PomModuleDetector();
        GradleModuleDetector gradleModuleDetector = new GradleModuleDetector();
        PomGavExtractor gavExtractor = new PomGavExtractor();
        ProjectContextFactory projectFactory = new DefaultProjectContextFactory(
                buildDetector, pomModuleDetector, gradleModuleDetector, gavExtractor);

        DependencyScanner dependencyScanner = buildDependencyScanner();
        JarIndexMatcher jarMatcher = new JarIndexMatcher();
        UsageMatcher usageMatcher = new UsageMatcher(jarMatcher);
        UnderuseCalculator underuseCalculator = new UnderuseCalculator();
        VersionChecker versionChecker = new VersionChecker(new MavenCentralVersionRegistry());

        this.auditUseCase = new AuditProjectService(
                projectFactory, resolverRegistry, dependencyScanner,
                usageMatcher, underuseCalculator, versionChecker);
        this.reportWriterResolver = new ReportWriterResolver();
    }

    private static DependencyScanner buildDependencyScanner() {
        VirtualThreadExecutorFactory executorFactory = new VirtualThreadExecutorFactory();
        JavaParserSourceScanner sourceScanner = new JavaParserSourceScanner(executorFactory);
        AsmBytecodeScanner bytecodeScanner = new AsmBytecodeScanner(executorFactory);
        CompositeResourceScanner resourceScanner = new CompositeResourceScanner();
        ServiceLoaderManifestReader manifestReader = new ServiceLoaderManifestReader();
        RelocationDetector relocationDetector = new RelocationDetector();
        JarManifestScanner jarManifestScanner = new JarManifestScanner();
        FrameworkDetector frameworkDetector = new FrameworkDetector();
        LocalJarIndexer jarIndexer = new LocalJarIndexer(
                manifestReader, relocationDetector, jarManifestScanner, frameworkDetector);
        LocalRepositoryLocator locator = buildCompositeLocator();
        com.kabusair.nexuscleaner.infrastructure.adapter.output.source.SpringComponentScanExtractor scanExtractor =
                new com.kabusair.nexuscleaner.infrastructure.adapter.output.source.SpringComponentScanExtractor();
        return new DependencyScanner(sourceScanner, bytecodeScanner, resourceScanner, jarIndexer, locator, scanExtractor);
    }

    private static LocalRepositoryLocator buildCompositeLocator() {
        MavenClasspathResolver mavenResolver = new MavenClasspathResolver(
                MavenClasspathResolver.detectMavenExecutable());
        InProjectModuleLinker moduleLinker = new InProjectModuleLinker();
        MavenLocalRepositoryLocator fallback = new MavenLocalRepositoryLocator();
        return new CompositeLocator(List.of(mavenResolver, moduleLinker, fallback));
    }

    public AuditProjectUseCase auditUseCase() {
        return auditUseCase;
    }

    public ReportWriterResolver reportWriterResolver() {
        return reportWriterResolver;
    }
}
