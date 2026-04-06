package com.kabusair.nexuscleaner.core.usecase;

import com.kabusair.nexuscleaner.core.domain.exception.ProjectParseException;
import com.kabusair.nexuscleaner.core.domain.model.AuditFinding;
import com.kabusair.nexuscleaner.core.domain.model.AuditReport;
import com.kabusair.nexuscleaner.core.domain.model.Dependency;
import com.kabusair.nexuscleaner.core.domain.model.DependencyCoordinate;
import com.kabusair.nexuscleaner.core.domain.model.DependencyExclusion;
import com.kabusair.nexuscleaner.core.domain.model.DependencyGraph;
import com.kabusair.nexuscleaner.core.domain.model.DependencyHealth;
import com.kabusair.nexuscleaner.core.domain.model.DependencyNode;
import com.kabusair.nexuscleaner.core.domain.model.DependencyScope;
import com.kabusair.nexuscleaner.core.domain.model.EvidenceLayer;
import com.kabusair.nexuscleaner.core.domain.model.FindingFlag;
import com.kabusair.nexuscleaner.core.domain.model.JarIndex;
import com.kabusair.nexuscleaner.core.domain.model.ProjectContext;
import com.kabusair.nexuscleaner.core.domain.model.UsageEvidence;
import com.kabusair.nexuscleaner.core.port.input.AuditOptions;
import com.kabusair.nexuscleaner.core.port.input.AuditProjectUseCase;
import com.kabusair.nexuscleaner.core.port.output.DependencyGraphResolver;
import com.kabusair.nexuscleaner.core.usecase.scanner.DependencyScanner;
import com.kabusair.nexuscleaner.core.usecase.scanner.ScanContext;
import com.kabusair.nexuscleaner.core.usecase.scanner.UnderuseCalculator;
import com.kabusair.nexuscleaner.core.usecase.scanner.UsageMatcher;
import com.kabusair.nexuscleaner.core.usecase.version.VersionChecker;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Primary use-case orchestrator. Applies scope-aware, exclusion-aware,
 * relocation-aware, annotation-processor-aware, framework-safety-net,
 * tree-validated transitive, and starter-propagation rules.
 */
public final class AuditProjectService implements AuditProjectUseCase {

    private final ProjectContextFactory projectContextFactory;
    private final ResolverRegistry resolverRegistry;
    private final DependencyScanner dependencyScanner;
    private final UsageMatcher usageMatcher;
    private final UnderuseCalculator underuseCalculator;
    private final VersionChecker versionChecker;

    public AuditProjectService(ProjectContextFactory projectContextFactory,
                               ResolverRegistry resolverRegistry,
                               DependencyScanner dependencyScanner,
                               UsageMatcher usageMatcher,
                               UnderuseCalculator underuseCalculator,
                               VersionChecker versionChecker) {
        this.projectContextFactory = projectContextFactory;
        this.resolverRegistry = resolverRegistry;
        this.dependencyScanner = dependencyScanner;
        this.usageMatcher = usageMatcher;
        this.underuseCalculator = underuseCalculator;
        this.versionChecker = versionChecker;
    }

    @Override
    public AuditReport audit(Path projectRoot, AuditOptions options) {
        ProjectContext project = projectContextFactory.create(projectRoot);
        DependencyGraph graph = resolveGraph(project);
        ScanContext scan = dependencyScanner.scan(project, graph);
        Map<DependencyCoordinate, List<UsageEvidence>> evidence = usageMatcher.match(scan);
        List<AuditFinding> findings = buildFindings(project, graph, scan, evidence, options);
        findings = applyTreeValidatedTransitiveFlag(findings, graph);
        findings = applyStarterHealthPropagation(findings, graph);
        findings = applySpringBootAutoPromotion(findings, scan);
        findings = applyComponentScanPromotion(findings, scan);
        return new AuditReport(project, findings, null);
    }

    private DependencyGraph resolveGraph(ProjectContext project) {
        DependencyGraphResolver resolver = resolverRegistry.resolverFor(project.buildSystem());
        if (resolver == null) {
            throw new ProjectParseException("No resolver available for " + project.buildSystem());
        }
        return resolver.resolve(project);
    }

    private List<AuditFinding> buildFindings(ProjectContext project,
                                             DependencyGraph graph,
                                             ScanContext scan,
                                             Map<DependencyCoordinate, List<UsageEvidence>> evidenceMap,
                                             AuditOptions options) {
        List<AuditFinding> findings = new ArrayList<>();
        for (Dependency dependency : graph.allByGa().values()) {
            if (project.isSelfGav(dependency.coordinate())) continue;
            findings.add(buildFinding(dependency, graph, scan, evidenceMap, options));
        }
        return findings;
    }

    private AuditFinding buildFinding(Dependency dependency,
                                      DependencyGraph graph,
                                      ScanContext scan,
                                      Map<DependencyCoordinate, List<UsageEvidence>> evidenceMap,
                                      AuditOptions options) {
        if (isExcluded(dependency, graph.exclusions())) {
            return finding(dependency, DependencyHealth.EXCLUDED, List.of(), null, Set.of(), "Intentionally excluded by user");
        }
        if (dependency.scope() == DependencyScope.IMPORT) {
            return finding(dependency, DependencyHealth.HEALTHY, List.of(), null, EnumSet.of(FindingFlag.BOM_ONLY), "BOM / import scope");
        }

        JarIndex index = scan.indicesByDependency().get(dependency.coordinate());
        List<UsageEvidence> evidence = evidenceMap.getOrDefault(dependency.coordinate(), List.of());
        boolean isAP = isAnnotationProcessor(index);
        boolean isFramework = isFrameworkDep(index) || isKnownFrameworkGroup(dependency.coordinate());
        boolean hasAutoConfig = hasAutoConfiguration(index);
        EnumSet<FindingFlag> flags = baseFlagsFor(dependency, evidence, isAP, isFramework, hasAutoConfig);

        if (hasNoEvidence(evidence, dependency.scope(), isAP)) {
            return applyFrameworkSafetyNet(dependency, evidence, flags, isFramework, hasAutoConfig);
        }

        return healthyOrUnderused(dependency, evidence, scan, flags, options);
    }

    private AuditFinding applyFrameworkSafetyNet(Dependency dependency,
                                                 List<UsageEvidence> evidence,
                                                 EnumSet<FindingFlag> flags,
                                                 boolean isFramework,
                                                 boolean hasAutoConfig) {
        if (isFramework) {
            return finding(dependency, DependencyHealth.INCONCLUSIVE, evidence, null, flags,
                    "Framework/runtime dependency — no direct usage but likely wired by container");
        }
        if (hasAutoConfig) {
            return finding(dependency, DependencyHealth.INCONCLUSIVE, evidence, null, flags,
                    "Carries auto-configuration — may be activated by Spring Boot without direct imports");
        }
        return finding(dependency, DependencyHealth.UNUSED, evidence, null, flags,
                "No usage evidence at any layer");
    }

    /**
     * Tree-validated REMOVABLE_WITH_PARENT: walks the full dependency tree to
     * verify that EVERY path from a direct dep to this transitive passes through
     * an unused direct. If even one live direct ancestor exists, the transitive
     * survives and is NOT flagged.
     */
    private List<AuditFinding> applyTreeValidatedTransitiveFlag(List<AuditFinding> findings,
                                                                DependencyGraph graph) {
        Set<String> unusedDirectGas = collectUnusedDirectGas(findings);
        if (unusedDirectGas.isEmpty()) return findings;

        TreePathValidator validator = TreePathValidator.from(graph);
        List<AuditFinding> result = new ArrayList<>(findings.size());
        for (AuditFinding f : findings) {
            result.add(maybeAddRemovableFlag(f, unusedDirectGas, validator));
        }
        return result;
    }

    private Set<String> collectUnusedDirectGas(List<AuditFinding> findings) {
        Set<String> gas = new HashSet<>();
        for (AuditFinding f : findings) {
            if (f.health() == DependencyHealth.UNUSED && f.dependency().directlyDeclared()) {
                gas.add(f.dependency().coordinate().ga());
            }
        }
        return gas;
    }

    private AuditFinding maybeAddRemovableFlag(AuditFinding f,
                                               Set<String> unusedDirectGas,
                                               TreePathValidator validator) {
        if (f.health() != DependencyHealth.UNUSED) return f;
        if (f.dependency().directlyDeclared()) return f;
        if (!f.flags().contains(FindingFlag.TRANSITIVE)) return f;
        if (!validator.isExclusivelyUnderUnusedDirects(f.dependency().coordinate(), unusedDirectGas)) return f;

        EnumSet<FindingFlag> newFlags = EnumSet.copyOf(f.flags());
        newFlags.add(FindingFlag.REMOVABLE_WITH_PARENT);
        return new AuditFinding(f.dependency(), f.health(), f.evidence(), f.latestVersion(),
                newFlags, "All paths lead through unused direct dependencies — will disappear when parents are removed");
    }

    /**
     * Recursive starter health propagation: walks the ENTIRE downward tree of
     * every INCONCLUSIVE node. If ANY descendant (child, grandchild, etc.) is
     * HEALTHY, UNDERUSED, or OUTDATED, the INCONCLUSIVE ancestor is promoted
     * to HEALTHY. This handles chains like starter → boot-module → library.
     */
    private List<AuditFinding> applyStarterHealthPropagation(List<AuditFinding> findings,
                                                             DependencyGraph graph) {
        Map<String, DependencyHealth> healthByGa = indexHealthByGa(findings);
        Set<String> promoted = new HashSet<>();
        collectPromotableNodes(graph, healthByGa, promoted);
        if (promoted.isEmpty()) return findings;

        List<AuditFinding> result = new ArrayList<>(findings.size());
        for (AuditFinding f : findings) {
            result.add(maybePromoteStarter(f, promoted));
        }
        return result;
    }

    private Map<String, DependencyHealth> indexHealthByGa(List<AuditFinding> findings) {
        Map<String, DependencyHealth> map = new HashMap<>();
        for (AuditFinding f : findings) {
            map.put(f.dependency().coordinate().ga(), f.health());
        }
        return map;
    }

    private void collectPromotableNodes(DependencyGraph graph,
                                        Map<String, DependencyHealth> healthByGa,
                                        Set<String> promoted) {
        for (DependencyNode root : graph.roots()) {
            walkAndPromote(root, healthByGa, promoted);
        }
    }

    private void walkAndPromote(DependencyNode node,
                                Map<String, DependencyHealth> healthByGa,
                                Set<String> promoted) {
        for (DependencyNode child : node.children()) {
            walkAndPromote(child, healthByGa, promoted);
        }
        String ga = node.dependency().coordinate().ga();
        DependencyHealth health = healthByGa.get(ga);
        if (health != DependencyHealth.INCONCLUSIVE) return;
        if (hasLiveDescendant(node, healthByGa, promoted)) {
            promoted.add(ga);
        }
    }

    private boolean hasLiveDescendant(DependencyNode parent,
                                     Map<String, DependencyHealth> healthByGa,
                                     Set<String> alreadyPromoted) {
        for (DependencyNode child : parent.children()) {
            String childGa = child.dependency().coordinate().ga();
            DependencyHealth childHealth = healthByGa.get(childGa);
            if (childHealth == DependencyHealth.HEALTHY || childHealth == DependencyHealth.UNDERUSED
                    || childHealth == DependencyHealth.OUTDATED) {
                return true;
            }
            if (alreadyPromoted.contains(childGa)) return true;
            if (hasLiveDescendant(child, healthByGa, alreadyPromoted)) return true;
        }
        return false;
    }

    private AuditFinding maybePromoteStarter(AuditFinding f, Set<String> promoted) {
        if (f.health() != DependencyHealth.INCONCLUSIVE) return f;
        if (!promoted.contains(f.dependency().coordinate().ga())) return f;
        return new AuditFinding(f.dependency(), DependencyHealth.HEALTHY, f.evidence(),
                f.latestVersion(), f.flags(),
                "Framework/starter with actively used descendants — aggregation is needed");
    }

    /**
     * Spring Boot auto-promotion: if {@code @SpringBootApplication} was detected
     * in any source file, all INCONCLUSIVE dependencies from the
     * {@code org.springframework.boot} group are promoted to HEALTHY. These are
     * auto-configured by Spring Boot's classpath scanning and are guaranteed to
     * be active in a running application.
     */
    private List<AuditFinding> applySpringBootAutoPromotion(List<AuditFinding> findings,
                                                            ScanContext scan) {
        if (!scan.springBootDetected()) return findings;
        List<AuditFinding> result = new ArrayList<>(findings.size());
        for (AuditFinding f : findings) {
            result.add(maybePromoteSpringBoot(f));
        }
        return result;
    }

    private AuditFinding maybePromoteSpringBoot(AuditFinding f) {
        if (f.health() != DependencyHealth.INCONCLUSIVE) return f;
        if (!isSpringBootGroup(f.dependency().coordinate().groupId())) return f;
        return new AuditFinding(f.dependency(), DependencyHealth.HEALTHY, f.evidence(),
                f.latestVersion(), f.flags(),
                "@SpringBootApplication detected — auto-configured by Spring Boot");
    }

    private boolean isSpringBootGroup(String groupId) {
        return "org.springframework.boot".equals(groupId);
    }

    /**
     * Component scan promotion: if {@code @SpringBootApplication(scanBasePackages)}
     * or {@code @ComponentScan(basePackages)} declares package prefixes, any
     * UNUSED or INCONCLUSIVE dependency whose exported classes fall under those
     * prefixes is promoted to INCONCLUSIVE (if UNUSED) or HEALTHY (if INCONCLUSIVE)
     * with a COMPONENT_SCANNED flag. Spring auto-discovers these at runtime.
     */
    private List<AuditFinding> applyComponentScanPromotion(List<AuditFinding> findings,
                                                           ScanContext scan) {
        Set<String> scanPackages = scan.componentScanPackages();
        if (scanPackages.isEmpty()) return findings;

        List<AuditFinding> result = new ArrayList<>(findings.size());
        for (AuditFinding f : findings) {
            result.add(maybePromoteByComponentScan(f, scanPackages, scan));
        }
        return result;
    }

    private AuditFinding maybePromoteByComponentScan(AuditFinding f,
                                                     Set<String> scanPackages,
                                                     ScanContext scan) {
        if (f.health() != DependencyHealth.UNUSED && f.health() != DependencyHealth.INCONCLUSIVE) return f;
        if (!dependencyMatchesScanPackages(f.dependency().coordinate(), scanPackages, scan)) return f;

        EnumSet<FindingFlag> newFlags = f.flags().isEmpty()
                ? EnumSet.of(FindingFlag.COMPONENT_SCANNED)
                : EnumSet.copyOf(f.flags());
        newFlags.add(FindingFlag.COMPONENT_SCANNED);

        if (f.health() == DependencyHealth.UNUSED) {
            return new AuditFinding(f.dependency(), DependencyHealth.INCONCLUSIVE, f.evidence(),
                    f.latestVersion(), newFlags,
                    "Package matches @ComponentScan — may contain auto-discovered Spring beans");
        }
        return new AuditFinding(f.dependency(), DependencyHealth.HEALTHY, f.evidence(),
                f.latestVersion(), newFlags,
                "Framework dependency under component-scanned package — auto-wired by Spring");
    }

    private boolean dependencyMatchesScanPackages(DependencyCoordinate coord,
                                                  Set<String> scanPackages,
                                                  ScanContext scan) {
        JarIndex index = scan.indicesByDependency().get(coord);
        if (index == null) return matchesByGroupId(coord, scanPackages);
        return matchesByExportedClasses(index, scanPackages);
    }

    private boolean matchesByGroupId(DependencyCoordinate coord, Set<String> scanPackages) {
        String groupId = coord.groupId();
        for (String pkg : scanPackages) {
            if (groupId.startsWith(pkg)) return true;
        }
        return false;
    }

    private boolean matchesByExportedClasses(JarIndex index, Set<String> scanPackages) {
        for (String fqcn : index.exportedClasses()) {
            for (String pkg : scanPackages) {
                if (fqcn.startsWith(pkg)) return true;
            }
        }
        return false;
    }

    private boolean isAnnotationProcessor(JarIndex index) {
        return index != null && index.isAnnotationProcessor();
    }

    private boolean isFrameworkDep(JarIndex index) {
        return index != null && index.isStarterOrFramework();
    }

    private static final Set<String> KNOWN_FRAMEWORK_GROUPS = Set.of(
            "org.springframework", "org.springframework.boot", "org.springframework.security",
            "org.springframework.data", "org.hibernate.orm", "org.hibernate.validator",
            "ch.qos.logback", "org.slf4j", "org.apache.logging.log4j",
            "org.apache.tomcat.embed", "org.eclipse.jetty", "io.netty", "io.micrometer",
            "org.aspectj", "com.zaxxer", "org.liquibase", "org.flywaydb",
            "org.apache.camel", "org.apache.activemq",
            "com.fasterxml.jackson.core", "com.fasterxml.jackson.datatype", "tools.jackson.core",
            "org.ehcache", "javax.cache"
    );

    private boolean isKnownFrameworkGroup(DependencyCoordinate coord) {
        return KNOWN_FRAMEWORK_GROUPS.contains(coord.groupId());
    }

    private boolean hasAutoConfiguration(JarIndex index) {
        return index != null && !index.autoConfigClasses().isEmpty();
    }

    private AuditFinding healthyOrUnderused(Dependency dependency,
                                            List<UsageEvidence> evidence,
                                            ScanContext scan,
                                            EnumSet<FindingFlag> flags,
                                            AuditOptions options) {
        String latest = resolveLatestVersion(dependency, options, flags);
        JarIndex index = scan.indicesByDependency().get(dependency.coordinate());
        double ratio = underuseCalculator.usageRatio(evidence, index);

        if (ratio < options.underuseThreshold()) {
            return finding(dependency, DependencyHealth.UNDERUSED, evidence, latest, flags,
                    "Usage ratio " + formatRatio(ratio) + " is below threshold");
        }
        DependencyHealth health = latest == null ? DependencyHealth.HEALTHY : DependencyHealth.OUTDATED;
        return finding(dependency, health, evidence, latest, flags, "Used across layers");
    }

    private String resolveLatestVersion(Dependency dependency, AuditOptions options, EnumSet<FindingFlag> flags) {
        if (!options.checkLatestVersions() || options.offline()) return null;
        Optional<String> latest = versionChecker.latestIfNewer(dependency.coordinate());
        if (latest.isEmpty()) return null;
        flags.add(FindingFlag.VERSION_AVAILABLE);
        return latest.get();
    }

    private EnumSet<FindingFlag> baseFlagsFor(Dependency dependency,
                                              List<UsageEvidence> evidence,
                                              boolean isAP,
                                              boolean isFramework,
                                              boolean hasAutoConfig) {
        EnumSet<FindingFlag> flags = EnumSet.noneOf(FindingFlag.class);
        if (!dependency.directlyDeclared()) flags.add(FindingFlag.TRANSITIVE);
        if (dependency.scope() == DependencyScope.PROVIDED) flags.add(FindingFlag.PROVIDED_SCOPE);
        if (isAP) flags.add(FindingFlag.ANNOTATION_PROCESSOR);
        if (isFramework) flags.add(FindingFlag.FRAMEWORK_RUNTIME);
        if (hasAutoConfig) flags.add(FindingFlag.AUTO_CONFIGURED);
        if (containsReflectionHint(evidence)) flags.add(FindingFlag.REFLECTION_SUSPECTED);
        return flags;
    }

    private boolean containsReflectionHint(List<UsageEvidence> evidence) {
        for (UsageEvidence e : evidence) {
            if (e.layer() == EvidenceLayer.BYTECODE_REFLECTION_HINT) return true;
        }
        return false;
    }

    private boolean hasNoEvidence(List<UsageEvidence> evidence, DependencyScope scope, boolean isAP) {
        if (evidence.isEmpty()) return true;
        if (isAP || !scope.requiresBytecodeEvidence()) {
            return !hasSourceLevelEvidence(evidence);
        }
        return !hasAnyStrongEvidence(evidence);
    }

    private boolean hasAnyStrongEvidence(List<UsageEvidence> evidence) {
        for (UsageEvidence e : evidence) {
            if (e.isStrong() || e.layer() == EvidenceLayer.BYTECODE_REFLECTION_HINT
                    || e.layer() == EvidenceLayer.RESOURCE) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSourceLevelEvidence(List<UsageEvidence> evidence) {
        for (UsageEvidence e : evidence) {
            if (e.layer() == EvidenceLayer.SOURCE_IMPORT || e.layer() == EvidenceLayer.SOURCE_ANNOTATION) {
                return true;
            }
        }
        return false;
    }

    private boolean isExcluded(Dependency dependency, Set<DependencyExclusion> exclusions) {
        for (DependencyExclusion rule : exclusions) {
            if (rule.matches(dependency.coordinate())) return true;
        }
        return false;
    }

    private AuditFinding finding(Dependency dependency,
                                 DependencyHealth health,
                                 List<UsageEvidence> evidence,
                                 String latestVersion,
                                 Set<FindingFlag> flags,
                                 String rationale) {
        return new AuditFinding(dependency, health, evidence, latestVersion, flags, rationale);
    }

    private String formatRatio(double ratio) {
        return String.format("%.1f%%", ratio * 100.0d);
    }
}
