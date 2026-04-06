package com.kabusair.nexuscleaner.infrastructure.adapter.output.resource;

import com.kabusair.nexuscleaner.core.domain.model.SymbolReference;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Extracts FQCNs from Spring XML config files ({@code applicationContext.xml},
 * {@code beans.xml}, etc.) and Camel XML routes ({@code camel-context.xml}).
 * Captures:
 * <ul>
 *   <li>{@code <bean class="com.example.Foo"/>} — Spring bean declarations</li>
 *   <li>{@code <from uri="activemq:queue:orders"/>} — Camel route endpoints</li>
 *   <li>{@code <to uri="ftp://server/path"/>} — Camel route targets</li>
 *   <li>{@code class} attributes on any element</li>
 * </ul>
 */
final class SpringXmlExtractor {

    private static final XMLInputFactory XML_FACTORY = createFactory();
    private final CamelUriMapper camelUriMapper;

    SpringXmlExtractor(CamelUriMapper camelUriMapper) {
        this.camelUriMapper = camelUriMapper;
    }

    void extract(Path xmlFile, Set<SymbolReference> sink) {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(xmlFile))) {
            XMLStreamReader reader = XML_FACTORY.createXMLStreamReader(in);
            try {
                read(reader, sink);
            } finally {
                reader.close();
            }
        } catch (XMLStreamException | IOException ignored) {
        }
    }

    private static final String[] FQCN_ATTRIBUTES = {"class", "name", "type", "value", "ref"};

    private void read(XMLStreamReader reader, Set<SymbolReference> sink) throws XMLStreamException {
        while (reader.hasNext()) {
            int event = reader.next();
            if (event != XMLStreamConstants.START_ELEMENT) continue;
            extractFqcnAttributes(reader, sink);
            extractCamelUri(reader, sink);
        }
    }

    private void extractFqcnAttributes(XMLStreamReader reader, Set<SymbolReference> sink) {
        for (String attr : FQCN_ATTRIBUTES) {
            String value = reader.getAttributeValue(null, attr);
            if (value == null || value.isBlank()) continue;
            String trimmed = value.trim();
            if (looksLikeFqcn(trimmed)) {
                sink.add(SymbolReference.reflectionHint(trimmed));
            }
        }
    }

    private void extractCamelUri(XMLStreamReader reader, Set<SymbolReference> sink) {
        String uri = reader.getAttributeValue(null, "uri");
        if (uri == null || uri.isBlank()) return;
        String scheme = extractScheme(uri.trim());
        if (scheme == null) return;
        String componentClass = camelUriMapper.resolve(scheme);
        if (componentClass != null) {
            sink.add(SymbolReference.reflectionHint(componentClass));
        }
    }

    private String extractScheme(String uri) {
        int colon = uri.indexOf(':');
        if (colon <= 0) return null;
        return uri.substring(0, colon);
    }

    private boolean looksLikeFqcn(String s) {
        return s.length() >= 3 && s.length() < 512 && s.contains(".") && !s.contains(" ");
    }

    private static XMLInputFactory createFactory() {
        XMLInputFactory f = XMLInputFactory.newInstance();
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        return f;
    }
}
