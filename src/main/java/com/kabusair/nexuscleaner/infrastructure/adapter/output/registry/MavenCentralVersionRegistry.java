package com.kabusair.nexuscleaner.infrastructure.adapter.output.registry;

import com.kabusair.nexuscleaner.core.domain.model.DependencyCoordinate;
import com.kabusair.nexuscleaner.core.port.output.VersionRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Looks up the latest version of a dependency from the Maven Central search
 * API. Results are cached for the lifetime of the process so the same GAV is
 * never requested twice per audit run. All parsing goes through Jackson 3's
 * immutable {@link JsonMapper}; no reflection is required, keeping the adapter
 * GraalVM-friendly.
 */
public final class MavenCentralVersionRegistry implements VersionRegistry {

    private static final String SEARCH_URL = "https://search.maven.org/solrsearch/select";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ObjectMapper jsonMapper;
    private final ConcurrentMap<String, Optional<String>> cache = new ConcurrentHashMap<>();

    public MavenCentralVersionRegistry() {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), JsonMapper.builder().build());
    }

    public MavenCentralVersionRegistry(HttpClient httpClient, ObjectMapper jsonMapper) {
        this.httpClient = httpClient;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Optional<String> latestVersion(DependencyCoordinate coordinate) {
        if (coordinate == null) return Optional.empty();
        return cache.computeIfAbsent(coordinate.ga(), key -> fetchLatest(coordinate));
    }

    private Optional<String> fetchLatest(DependencyCoordinate coordinate) {
        try {
            HttpResponse<String> response = sendRequest(buildRequestUri(coordinate));
            if (response.statusCode() != 200) return Optional.empty();
            return parseLatestVersion(response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private URI buildRequestUri(DependencyCoordinate coordinate) {
        String query = "g:\"" + coordinate.groupId() + "\" AND a:\"" + coordinate.artifactId() + "\"";
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return URI.create(SEARCH_URL + "?q=" + encoded + "&rows=1&wt=json");
    }

    private HttpResponse<String> sendRequest(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private Optional<String> parseLatestVersion(String body) {
        try {
            JsonNode root = jsonMapper.readTree(body);
            JsonNode docs = root.path("response").path("docs");
            if (!docs.isArray() || docs.isEmpty()) return Optional.empty();
            JsonNode latest = docs.get(0).path("latestVersion");
            return latest.isString() ? Optional.of(latest.asString()) : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
