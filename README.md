# NexusCleaner

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![License](https://img.shields.io/badge/License-MIT-2EA043?style=for-the-badge)](LICENSE)
[![Build](https://img.shields.io/badge/Build-Passing-brightgreen?style=for-the-badge)]()
[![GraalVM](https://img.shields.io/badge/GraalVM-Native_Ready-EE4C2C?style=for-the-badge&logo=graalvm&logoColor=white)]()
[![Architecture](https://img.shields.io/badge/Architecture-Clean-blueviolet?style=for-the-badge)]()

High-accuracy unused dependency auditor for any project — Maven, Gradle, and more ecosystems coming (Flutter, Angular, React). Enterprise-grade precision with zero false positives on framework dependencies.

Built with Java 21, Clean Architecture, ASM bytecode analysis, and Virtual Threads.

---

## Console Output

```
NexusCleaner audit
Project : /home/user/my-enterprise-app
Build   : MAVEN
Modules : 27 (common, payments-core, billing-service, ...)
Sources : 28 main roots, 14 test roots
When    : 2026-04-06T12:59:56Z

[UNUSED] 199
  - commons-dbcp:commons-dbcp:1.4  (scope=COMPILE)
      reason : No usage evidence at any layer
  - ch.qos.reload4j:reload4j:1.2.21  (scope=COMPILE)
      reason : No usage evidence at any layer
  - com.google.inject:guice:5.1.0  (scope=COMPILE)
      reason : No usage evidence at any layer
  - org.jvnet.jaxb2.maven2:maven-jaxb2-plugin:0.15.3  (scope=COMPILE)
      reason : No usage evidence at any layer
  - com.example.internal:export-rest:v20.5.1  (scope=COMPILE)
      reason : No usage evidence at any layer
  ...

[UNDERUSED] 73
  - org.hibernate.orm:hibernate-core:7.2.1.Final  (scope=COMPILE)
      reason : Usage ratio 0.2% is below threshold
      flags  : REFLECTION_SUSPECTED, FRAMEWORK_RUNTIME
  - com.google.guava:guava:32.1.1-jre  (scope=COMPILE)
      reason : Usage ratio 0.3% is below threshold
  - org.springframework:spring-core:7.0.6  (scope=COMPILE)
      reason : Usage ratio 0.6% is below threshold
      flags  : AUTO_CONFIGURED, FRAMEWORK_RUNTIME
  ...

[HEALTHY] 101
  - org.springframework.boot:spring-boot-starter-data-jpa:4.0.5
      flags  : FRAMEWORK_RUNTIME, TRANSITIVE
  - org.ehcache:ehcache:3.11.1
      flags  : FRAMEWORK_RUNTIME
  ...

[INCONCLUSIVE] 221
  - org.apache.camel:camel-core:4.12.0  (scope=COMPILE)
      reason : Framework/runtime dependency — no direct usage but likely wired by container
      flags  : FRAMEWORK_RUNTIME
  ...

Summary
  total        : 600
  unused       : 199
  underused    : 73
  outdated     : 0
  healthy      : 101
  inconclusive : 221
  excluded     : 6
```

## JSON Output (Grouped by Health)

```json
{
  "metadata": {
    "project": "/home/user/my-enterprise-app",
    "buildSystem": "MAVEN",
    "generatedAt": "2026-04-06T12:59:56Z",
    "multiModule": true,
    "modules": ["common", "payments-core", "billing-service", "..."],
    "sourceRoots": 28,
    "testRoots": 14,
    "summary": {
      "total": 600,
      "unused": 199,
      "underused": 73,
      "outdated": 0,
      "healthy": 101,
      "inconclusive": 221,
      "excluded": 6
    }
  },
  "results": {
    "unused": [
      {
        "groupId": "commons-dbcp",
        "artifactId": "commons-dbcp",
        "version": "1.4",
        "scope": "COMPILE",
        "direct": true,
        "rationale": "No usage evidence at any layer",
        "flags": []
      }
    ],
    "underused": [
      {
        "groupId": "org.hibernate.orm",
        "artifactId": "hibernate-core",
        "version": "7.2.1.Final",
        "scope": "COMPILE",
        "direct": true,
        "usageRatio": "0.2%",
        "rationale": "Usage ratio 0.2% is below threshold",
        "flags": ["REFLECTION_SUSPECTED", "FRAMEWORK_RUNTIME"]
      }
    ],
    "healthy": [...],
    "inconclusive": [...],
    "excluded": [...]
  }
}
```

---

## Key Features

- **Multi-module support** -- recursively crawls Maven `<modules>` and Gradle `include` directives, merging all source/class/resource roots into a single scan surface
- **Deep bytecode analysis** -- ASM visitor-based scanner extracts type references, method calls, field accesses, annotations, lambda targets, method handles, and reflection hints (`Class.forName`, `ServiceLoader.load`, etc.)
- **Resource file scanning** -- extracts FQCNs from `application.properties`, `application.yml`, `persistence.xml`, `META-INF/services/*`, `META-INF/spring.factories`, and `META-INF/spring/*.imports`
- **Framework safety net** -- Spring Boot starters, Hibernate, logging backends, Camel, and other known framework runtimes are flagged as INCONCLUSIVE instead of UNUSED to prevent accidental deletion
- **Auto-config detection** -- scans `spring.factories` and `spring/*.imports` inside dependency JARs to identify auto-configured beans
- **Annotation processor awareness** -- Lombok, MapStruct, and similar tools are detected via `META-INF/services/javax.annotation.processing.Processor` and validated via source-level evidence only
- **Scope awareness** -- PROVIDED dependencies (Lombok, servlet-api) are never flagged as unused due to missing bytecode; RUNTIME dependencies are matched by bytecode only
- **Exclusion respect** -- `<exclusions>` in POM are tracked and reported as intentional, not as missing dependencies
- **Shade/relocation handling** -- detects relocated packages and matches symbols against both original and shaded class names
- **Smart transitive flag** -- `REMOVABLE_WITH_PARENT` marks transitives that will disappear when their unused direct parent is removed
- **Self-GAV exclusion** -- the project's own modules are automatically excluded from findings
- **Zero-config JAR resolution** -- `mvn dependency:build-classpath` resolves absolute paths respecting `settings.xml`, mirrors, private Nexus/Artifactory repos, and custom `.m2` locations without any user configuration
- **In-project module linking** -- sibling submodules are resolved from `target/classes` even if never `mvn install`ed
- **GraalVM native-image ready** -- no runtime reflection; manual DI; Picocli annotation processing at build time

## Prerequisites

- Java 21+
- Maven 3.6+ (to build NexusCleaner itself)
- The target project must have been compiled (`mvn compile` or `gradle build`) so that `.class` files exist

## Build

### Fat JAR

```bash
JAVA_HOME=/usr/lib/jvm/jdk-21 mvn clean package -DskipTests
```

Produces a self-contained fat JAR at `target/nexus-cleaner.jar`.

### Native binary (requires GraalVM)

```bash
JAVA_HOME=/path/to/graalvm-21 mvn clean package -Pnative -DskipTests
```

Produces a single `target/nexus-cleaner` binary with sub-second startup.

## Usage

```bash
java -jar target/nexus-cleaner.jar [OPTIONS] <project-path>
```

### Arguments

| Argument | Description |
|---|---|
| `<project-path>` | Path to the root of the Maven or Gradle project to audit. Defaults to `.` (current directory). |

### Options

| Option | Default | Description |
|---|---|---|
| `-f, --format` | `CONSOLE` | Output format. `CONSOLE` for human-readable text, `JSON` for machine-readable output. |
| `--no-version-check` | `false` | Skip Maven Central lookups. No network calls will be made. OUTDATED findings will not appear. |
| `--offline` | `false` | Do not contact any remote registry. Same effect as `--no-version-check`. |
| `--include-test` | `true` | Include test-scope dependencies in the audit. |
| `--underuse-threshold` | `0.05` | API usage ratio (0.0 to 1.0) below which a dependency is marked UNDERUSED. Default is 5%. |
| `--fail-on-issues` | `false` | Exit with code `1` if any UNUSED or OUTDATED findings exist. Designed for CI pipelines. |
| `-h, --help` | | Show help message and exit. |
| `-V, --version` | | Print version and exit. |

### Exit Codes

| Code | Meaning |
|---|---|
| `0` | Audit completed successfully (or issues found but `--fail-on-issues` not set). |
| `1` | Issues found and `--fail-on-issues` is set. |
| `2` | Audit failed due to an error (e.g. project not found, Maven not installed). |

## Examples

### 1. Quick local scan (no internet)

```bash
java -jar target/nexus-cleaner.jar --no-version-check /path/to/project
```

Scans the project without contacting Maven Central. Faster, works offline. OUTDATED findings will not appear.

### 2. Multi-module Maven project

```bash
java -jar target/nexus-cleaner.jar --no-version-check /path/to/parent-pom-project
```

Automatically detects `<modules>`, recursively crawls all submodules, merges their sources and bytecode, then audits the unified dependency graph. Output includes module count and source root summary.

### 3. JSON output for CI

```bash
java -jar target/nexus-cleaner.jar -f JSON --fail-on-issues /path/to/project
```

JSON output for programmatic parsing. Contacts Maven Central for version checks. Exits with code `1` if UNUSED or OUTDATED dependencies are found -- designed to fail a CI build gate.

### 4. Strict underuse threshold

```bash
java -jar target/nexus-cleaner.jar --underuse-threshold 0.10 /path/to/project
```

Flags any dependency where less than 10% of its public API is referenced. More aggressive than the default 5%.

### 5. Skip test dependencies

```bash
java -jar target/nexus-cleaner.jar --include-test=false /path/to/project
```

Ignores JUnit, Mockito, and all test-scope dependencies.

### 6. Full CI configuration

```bash
java -jar target/nexus-cleaner.jar \
  -f JSON \
  --fail-on-issues \
  --underuse-threshold 0.03 \
  --include-test=false \
  /path/to/project
```

JSON output, fail on issues, 3% underuse threshold, skip test deps.

## Health Categories

| Health | Meaning |
|---|---|
| UNUSED | No trace found in source imports, bytecode, annotations, reflection hints, or resource files. Safe to remove. |
| UNDERUSED | Used, but less than the threshold percentage of its public API is referenced. Candidate for a lighter alternative. |
| OUTDATED | Used and healthy, but a newer version exists on Maven Central. |
| HEALTHY | Used and on the latest version. Nothing to do. |
| INCONCLUSIVE | Framework runtime or auto-configured dependency with no direct usage. Likely wired by the container. Review manually before removing. |
| EXCLUDED | Intentionally excluded via `<exclusions>` in the POM. Reported for visibility, not as a problem. |

## Finding Flags

| Flag | Meaning |
|---|---|
| TRANSITIVE | Pulled in by another dependency, not declared directly by the user. |
| REMOVABLE_WITH_PARENT | Transitive of an UNUSED direct dependency -- will disappear when the parent is removed. |
| PROVIDED_SCOPE | Compile-time only dependency (e.g. Lombok). Verified via source-level evidence only. |
| ANNOTATION_PROCESSOR | JAR declares `META-INF/services/javax.annotation.processing.Processor`. Validated by source annotations. |
| FRAMEWORK_RUNTIME | Known framework group (Spring, Hibernate, Camel, etc.) or starter/meta-dependency. |
| AUTO_CONFIGURED | JAR carries `META-INF/spring.factories` or `META-INF/spring/*.imports` with auto-config classes. |
| REFLECTION_SUSPECTED | A string constant matching a class name was passed to `Class.forName()` or similar. |
| COMPONENT_SCANNED | Dependency's package matches `@SpringBootApplication(scanBasePackages)` or `@ComponentScan(basePackages)` -- auto-discovered by Spring at runtime. |
| VERSION_AVAILABLE | A newer version exists on Maven Central. |
| BOM_ONLY | Import-scope BOM. Never appears on the classpath. |

## How It Works

```
1. DETECT   -- Identify build system (Maven/Gradle), discover modules
2. RESOLVE  -- Run mvn dependency:tree to build the full dependency graph
3. LOCATE   -- mvn dependency:build-classpath resolves absolute JAR paths
               (respects settings.xml, mirrors, private repos)
             -- In-project module linker maps sibling modules to target/classes
             -- Fallback to standard ~/.m2/repository layout
4. INDEX    -- For each resolved JAR: collect exported classes, SPI impls,
               auto-config manifests, shade relocations, AP detection
5. SCAN     -- Source: JavaParser extracts imports + annotations
             -- Bytecode: ASM extracts types, methods, fields, lambdas,
                method handles, invokedynamic targets, reflection hints
             -- Resources: FQCNs from .properties, .yml, persistence.xml,
                spring.factories, META-INF/services
6. MATCH    -- Map every scanned symbol to the dependency that provides it
7. VERDICT  -- Apply scope rules, framework safety net, underuse calculation,
               exclusion checking, version comparison, smart transitive logic
```

## Architecture

```
core/
  domain/model/         Pure records and enums (no framework imports)
  domain/exception/     Domain exceptions
  port/input/           Primary port (AuditProjectUseCase)
  port/output/          Secondary ports (8 interfaces)
  usecase/              Orchestrator, scanners, matchers, version checker

infrastructure/
  adapter/input/cli/          Picocli commands and report writers (Console, JSON)
  adapter/output/build/       Maven POM/tree parser, Gradle module detector
  adapter/output/bytecode/    ASM class/method visitors with reflection detection
  adapter/output/source/      JavaParser-based import + annotation scanner
  adapter/output/resource/    Properties, YAML, Spring factories, SPI, persistence.xml
  adapter/output/jar/         JAR indexer, SPI reader, relocation detector,
                              framework detector, JAR manifest scanner
  adapter/output/repository/  Maven classpath resolver, Gradle classpath resolver,
                              in-project module linker, composite locator chain
  adapter/output/registry/    Maven Central version lookup (Jackson 3)
  concurrent/                 Virtual thread executor factory

bootstrap/
  ApplicationWiring.java      Manual DI composition root (GraalVM-ready)
```

## License

This project is licensed under the [MIT License](LICENSE).

Copyright (c) 2026 Khaled AbuSair
