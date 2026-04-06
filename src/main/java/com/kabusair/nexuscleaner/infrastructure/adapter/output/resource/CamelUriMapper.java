package com.kabusair.nexuscleaner.infrastructure.adapter.output.resource;

import java.util.Map;

/**
 * Maps Camel URI schemes to the FQCN of the component class that handles them.
 * When source code or XML contains {@code from("activemq:...")}, this mapper
 * resolves {@code activemq} to {@code org.apache.camel.component.activemq.ActiveMQComponent},
 * producing evidence that the {@code camel-activemq} dependency is used.
 */
final class CamelUriMapper {

    private static final Map<String, String> SCHEME_TO_CLASS = Map.ofEntries(
            Map.entry("activemq", "org.apache.camel.component.activemq.ActiveMQComponent"),
            Map.entry("jms", "org.apache.camel.component.jms.JmsComponent"),
            Map.entry("ftp", "org.apache.camel.component.file.remote.FtpComponent"),
            Map.entry("ftps", "org.apache.camel.component.file.remote.FtpsComponent"),
            Map.entry("sftp", "org.apache.camel.component.file.remote.SftpComponent"),
            Map.entry("file", "org.apache.camel.component.file.FileComponent"),
            Map.entry("csv", "org.apache.camel.dataformat.csv.CsvDataFormat"),
            Map.entry("velocity", "org.apache.camel.component.velocity.VelocityComponent"),
            Map.entry("quartz", "org.apache.camel.component.quartz.QuartzComponent"),
            Map.entry("crypto", "org.apache.camel.component.crypto.DigitalSignatureComponent"),
            Map.entry("base64", "org.apache.camel.dataformat.base64.Base64DataFormat"),
            Map.entry("zipfile", "org.apache.camel.dataformat.zipfile.ZipFileDataFormat"),
            Map.entry("mqtt", "org.apache.activemq.transport.mqtt.MQTTTransportFilter"),
            Map.entry("stomp", "org.apache.activemq.transport.stomp.StompTransportFilter"),
            Map.entry("amqp", "org.apache.activemq.transport.amqp.AmqpTransportFilter"),
            Map.entry("http", "org.apache.camel.component.http.HttpComponent"),
            Map.entry("https", "org.apache.camel.component.http.HttpComponent"),
            Map.entry("timer", "org.apache.camel.component.timer.TimerComponent"),
            Map.entry("direct", "org.apache.camel.component.direct.DirectComponent"),
            Map.entry("seda", "org.apache.camel.component.seda.SedaComponent"),
            Map.entry("bean", "org.apache.camel.component.bean.BeanComponent"),
            Map.entry("log", "org.apache.camel.component.log.LogComponent"),
            Map.entry("mock", "org.apache.camel.component.mock.MockComponent"),
            Map.entry("spring-ws", "org.springframework.ws.client.core.WebServiceTemplate")
    );

    String resolve(String scheme) {
        if (scheme == null) return null;
        return SCHEME_TO_CLASS.get(scheme.toLowerCase());
    }
}
