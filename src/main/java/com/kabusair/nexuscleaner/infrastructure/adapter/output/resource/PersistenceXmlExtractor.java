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
 * Extracts FQCNs from {@code persistence.xml}: the JPA provider class and
 * all {@code <class>} elements declaring managed entities.
 */
final class PersistenceXmlExtractor {

    private static final XMLInputFactory XML_FACTORY = createFactory();

    void extract(Path persistenceXml, Set<SymbolReference> sink) {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(persistenceXml))) {
            XMLStreamReader reader = XML_FACTORY.createXMLStreamReader(in);
            try {
                read(reader, sink);
            } finally {
                reader.close();
            }
        } catch (XMLStreamException | IOException ignored) {
        }
    }

    private void read(XMLStreamReader reader, Set<SymbolReference> sink) throws XMLStreamException {
        while (reader.hasNext()) {
            int event = reader.next();
            if (event != XMLStreamConstants.START_ELEMENT) continue;
            String name = reader.getLocalName();
            if ("provider".equals(name) || "class".equals(name)) {
                String text = reader.getElementText().trim();
                if (!text.isEmpty()) {
                    sink.add(SymbolReference.reflectionHint(text));
                }
            }
        }
    }

    private static XMLInputFactory createFactory() {
        XMLInputFactory f = XMLInputFactory.newInstance();
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        return f;
    }
}
