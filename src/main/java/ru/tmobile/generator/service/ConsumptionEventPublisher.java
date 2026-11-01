package ru.tmobile.generator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import ru.tmobile.generator.model.ConsumptionEvent;

@Service
public class ConsumptionEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(ConsumptionEventPublisher.class);
    private static final String CONSUMPTION_EVENT_TOPIC = "consumption-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ConsumptionEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(ConsumptionEvent event) {
        String key = event.userId() != null ? event.userId().toString() : null;
        kafkaTemplate.send(CONSUMPTION_EVENT_TOPIC, key, event).whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("[Kafka] ConsumptionEvent published: {}", event);
            } else {
                log.error("[Kafka] Failed to publish ConsumptionEvent for user {}", event.userId(), ex);
            }
        });
    }
}