package com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output;

import com.kabusair.nexuscleaner.core.domain.exception.NexusCleanerException;
import com.kabusair.nexuscleaner.core.domain.model.AuditReport;
import com.kabusair.nexuscleaner.core.port.output.ReportWriter;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto.JsonDependencyId;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto.JsonExcludedFinding;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto.JsonGroupedReport;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto.JsonGroupedResults;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto.JsonHealthyFinding;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto.JsonInconclusiveFinding;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto.JsonMetadata;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto.JsonOutdatedFinding;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto.JsonSummary;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto.JsonUnderusedFinding;
import com.kabusair.nexuscleaner.infrastructure.adapter.input.cli.output.dto.JsonUnusedFinding;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.PrintStream;
import java.util.List;

/**
 * Produces a grouped JSON report where findings are organized by health
 * category, each showing only the fields relevant to that verdict.
 * Metadata and summary sit at the root for quick machine consumption.
 */
public final class JsonReportWriter implements ReportWriter {

    private final ObjectMapper mapper;
    private final PrintStream out;
    private final FindingGrouper grouper;

    public JsonReportWriter(PrintStream out) {
        this(out, JsonMapper.builder().build());
    }

    public JsonReportWriter(PrintStream out, ObjectMapper mapper) {
        this.out = out;
        this.mapper = mapper;
        this.grouper = new FindingGrouper();
    }

    @Override
    public void write(AuditReport report) {
        try {
            JsonGroupedReport grouped = grouper.group(report);
            ObjectNode root = renderReport(grouped);
            out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (RuntimeException e) {
            throw new NexusCleanerException("Failed to render JSON report", e);
        }
    }

    private ObjectNode renderReport(JsonGroupedReport grouped) {
        ObjectNode root = mapper.createObjectNode();
        root.set("metadata", renderMetadata(grouped.metadata()));
        root.set("results", renderResults(grouped.results()));
        return root;
    }

    private ObjectNode renderMetadata(JsonMetadata m) {
        ObjectNode node = mapper.createObjectNode();
        node.put("project", m.project());
        node.put("buildSystem", m.buildSystem());
        node.put("generatedAt", m.generatedAt());
        node.put("multiModule", m.multiModule());
        if (m.multiModule()) {
            ArrayNode modules = mapper.createArrayNode();
            m.modules().forEach(modules::add);
            node.set("modules", modules);
            node.put("sourceRoots", m.sourceRoots());
            node.put("testRoots", m.testRoots());
        }
        node.set("summary", renderSummary(m.summary()));
        return node;
    }

    private ObjectNode renderSummary(JsonSummary s) {
        ObjectNode node = mapper.createObjectNode();
        node.put("total", s.total());
        node.put("unused", s.unused());
        node.put("underused", s.underused());
        node.put("outdated", s.outdated());
        node.put("healthy", s.healthy());
        node.put("inconclusive", s.inconclusive());
        node.put("excluded", s.excluded());
        return node;
    }

    private ObjectNode renderResults(JsonGroupedResults r) {
        ObjectNode node = mapper.createObjectNode();
        node.set("unused", renderUnusedList(r.unused()));
        node.set("underused", renderUnderusedList(r.underused()));
        node.set("outdated", renderOutdatedList(r.outdated()));
        node.set("healthy", renderHealthyList(r.healthy()));
        node.set("inconclusive", renderInconclusiveList(r.inconclusive()));
        node.set("excluded", renderExcludedList(r.excluded()));
        return node;
    }

    private ArrayNode renderUnusedList(List<JsonUnusedFinding> findings) {
        ArrayNode array = mapper.createArrayNode();
        for (JsonUnusedFinding f : findings) {
            ObjectNode node = renderDependencyId(f.dependency());
            node.put("rationale", f.rationale());
            node.set("flags", toStringArray(f.flags()));
            array.add(node);
        }
        return array;
    }

    private ArrayNode renderUnderusedList(List<JsonUnderusedFinding> findings) {
        ArrayNode array = mapper.createArrayNode();
        for (JsonUnderusedFinding f : findings) {
            ObjectNode node = renderDependencyId(f.dependency());
            node.put("usageRatio", f.usageRatio());
            node.put("rationale", f.rationale());
            node.set("flags", toStringArray(f.flags()));
            array.add(node);
        }
        return array;
    }

    private ArrayNode renderOutdatedList(List<JsonOutdatedFinding> findings) {
        ArrayNode array = mapper.createArrayNode();
        for (JsonOutdatedFinding f : findings) {
            ObjectNode node = renderDependencyId(f.dependency());
            node.put("latestVersion", f.latestVersion());
            node.set("flags", toStringArray(f.flags()));
            array.add(node);
        }
        return array;
    }

    private ArrayNode renderHealthyList(List<JsonHealthyFinding> findings) {
        ArrayNode array = mapper.createArrayNode();
        for (JsonHealthyFinding f : findings) {
            ObjectNode node = renderDependencyId(f.dependency());
            node.set("flags", toStringArray(f.flags()));
            array.add(node);
        }
        return array;
    }

    private ArrayNode renderInconclusiveList(List<JsonInconclusiveFinding> findings) {
        ArrayNode array = mapper.createArrayNode();
        for (JsonInconclusiveFinding f : findings) {
            ObjectNode node = renderDependencyId(f.dependency());
            node.put("rationale", f.rationale());
            node.set("flags", toStringArray(f.flags()));
            array.add(node);
        }
        return array;
    }

    private ArrayNode renderExcludedList(List<JsonExcludedFinding> findings) {
        ArrayNode array = mapper.createArrayNode();
        for (JsonExcludedFinding f : findings) {
            ObjectNode node = renderDependencyId(f.dependency());
            node.put("rationale", f.rationale());
            array.add(node);
        }
        return array;
    }

    private ObjectNode renderDependencyId(JsonDependencyId id) {
        ObjectNode node = mapper.createObjectNode();
        node.put("groupId", id.groupId());
        node.put("artifactId", id.artifactId());
        node.put("version", id.version());
        node.put("scope", id.scope());
        node.put("direct", id.direct());
        return node;
    }

    private ArrayNode toStringArray(List<String> items) {
        ArrayNode array = mapper.createArrayNode();
        if (items != null) items.forEach(array::add);
        return array;
    }
}
