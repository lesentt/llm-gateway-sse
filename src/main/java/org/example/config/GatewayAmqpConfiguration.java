package org.example.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayAmqpConfiguration {

    @Bean
    public TopicExchange gatewayEventsExchange(GatewayM4Properties gatewayM4Properties) {
        return new TopicExchange(gatewayM4Properties.getEvents().getExchange(), true, false);
    }

    @Bean
    public Queue requestCompletedQueue() {
        return new Queue("llm.gateway.request.completed", true);
    }

    @Bean
    public Binding requestCompletedBinding(
            Queue requestCompletedQueue,
            TopicExchange gatewayEventsExchange,
            GatewayM4Properties gatewayM4Properties
    ) {
        return BindingBuilder.bind(requestCompletedQueue)
                .to(gatewayEventsExchange)
                .with(gatewayM4Properties.getEvents().getRoutingKey());
    }
}
