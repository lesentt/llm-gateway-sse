package org.example.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.GatewayM4Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitRequestCompletedPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitRequestCompletedPublisher.class);

    private final GatewayM4Properties gatewayM4Properties;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public RabbitRequestCompletedPublisher(
            GatewayM4Properties gatewayM4Properties,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper
    ) {
        this.gatewayM4Properties = gatewayM4Properties;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(StreamCompletionRecord record) {
        if (!gatewayM4Properties.getEvents().isEnabled()) {
            return;
        }
        String payload = toJson(record);
        rabbitTemplate.convertAndSend(
                gatewayM4Properties.getEvents().getExchange(),
                gatewayM4Properties.getEvents().getRoutingKey(),
                payload
        );
    }

    private String toJson(StreamCompletionRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException ex) {
            log.warn("requestId={} requestCompletedSerializeFailed reason={}", record.requestId(), ex.getMessage());
            return "{}";
        }
    }
}
