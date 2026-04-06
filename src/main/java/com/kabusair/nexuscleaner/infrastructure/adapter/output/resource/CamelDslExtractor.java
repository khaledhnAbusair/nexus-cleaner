package com.kabusair.nexuscleaner.infrastructure.adapter.output.resource;

import com.kabusair.nexuscleaner.core.domain.model.SymbolReference;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans Java source files for Camel DSL patterns that reference components
 * without producing import statements or direct type references. Catches:
 * <ul>
 *   <li>{@code from("activemq:queue:orders")} and {@code .to("ftp://server")}</li>
 *   <li>{@code .marshal().csv()} and {@code .unmarshal().json()}</li>
 *   <li>{@code .marshal(new ZipFileDataFormat())} via the dataformat name</li>
 * </ul>
 *
 * <p>Each matched pattern emits a {@code REFLECTION_HINT} for the component's
 * FQCN so the matcher can resolve it against the JAR index.
 */
final class CamelDslExtractor {

    private static final Pattern URI_PATTERN =
            Pattern.compile("(?:from|to|toD|toF|wireTap|enrich|pollEnrich)\\s*\\(\\s*\"([a-zA-Z][\\w-]*):");

    private static final Pattern MARSHAL_PATTERN =
            Pattern.compile("\\.(?:marshal|unmarshal)\\(\\)\\.([a-zA-Z]\\w*)\\(");

    private static final Map<String, String> DSL_METHOD_TO_CLASS = Map.ofEntries(
            Map.entry("csv", "org.apache.camel.dataformat.csv.CsvDataFormat"),
            Map.entry("json", "org.apache.camel.component.jackson.JacksonDataFormat"),
            Map.entry("jacksonXml", "org.apache.camel.component.jacksonxml.JacksonXMLDataFormat"),
            Map.entry("jaxb", "org.apache.camel.converter.jaxb.JaxbDataFormat"),
            Map.entry("base64", "org.apache.camel.dataformat.base64.Base64DataFormat"),
            Map.entry("zipFile", "org.apache.camel.dataformat.zipfile.ZipFileDataFormat"),
            Map.entry("zipDeflater", "org.apache.camel.dataformat.deflater.ZipDeflaterDataFormat"),
            Map.entry("gzipDeflater", "org.apache.camel.dataformat.deflater.GzipDeflaterDataFormat"),
            Map.entry("soapjaxb", "org.apache.camel.dataformat.soap.SoapJaxbDataFormat"),
            Map.entry("xmlBeans", "org.apache.camel.converter.xmlbeans.XmlBeansDataFormat"),
            Map.entry("protobuf", "org.apache.camel.dataformat.protobuf.ProtobufDataFormat"),
            Map.entry("avro", "org.apache.camel.dataformat.avro.AvroDataFormat"),
            Map.entry("yaml", "org.apache.camel.component.snakeyaml.SnakeYAMLDataFormat")
    );

    private final CamelUriMapper uriMapper;

    CamelDslExtractor(CamelUriMapper uriMapper) {
        this.uriMapper = uriMapper;
    }

    void extract(Path javaFile, Set<SymbolReference> sink) {
        try (BufferedReader reader = Files.newBufferedReader(javaFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                extractUriPatterns(line, sink);
                extractMarshalPatterns(line, sink);
            }
        } catch (IOException ignored) {
        }
    }

    private void extractUriPatterns(String line, Set<SymbolReference> sink) {
        Matcher matcher = URI_PATTERN.matcher(line);
        while (matcher.find()) {
            String scheme = matcher.group(1);
            String componentClass = uriMapper.resolve(scheme);
            if (componentClass != null) {
                sink.add(SymbolReference.reflectionHint(componentClass));
            }
        }
    }

    private void extractMarshalPatterns(String line, Set<SymbolReference> sink) {
        Matcher matcher = MARSHAL_PATTERN.matcher(line);
        while (matcher.find()) {
            String method = matcher.group(1);
            String dataFormatClass = DSL_METHOD_TO_CLASS.get(method);
            if (dataFormatClass != null) {
                sink.add(SymbolReference.reflectionHint(dataFormatClass));
            }
        }
    }
}
