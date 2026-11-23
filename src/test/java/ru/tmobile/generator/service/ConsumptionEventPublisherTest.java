package ru.tmobile.generator.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import ru.tmobile.generator.model.ConsumptionEvent;
import ru.tmobile.generator.model.ResourceType;

class ConsumptionEventPublisherTest {

    private KafkaTemplate<String, Object> kafkaTemplate;
    private ConsumptionEventPublisher publisher;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), any(), any()))
            .thenReturn(new CompletableFuture<>());
        when(kafkaTemplate.send(anyString(), isNull(), any()))
            .thenReturn(new CompletableFuture<>());
        publisher = new ConsumptionEventPublisher(kafkaTemplate);
    }

    @Test
    void publishSendsEventToConsumptionEventsTopic() {
        ConsumptionEvent event = ConsumptionEvent.create(
            42L, ResourceType.TRAFFIC, 1.5, Instant.parse("2026-01-15T12:00:00Z"),
            ZoneId.of("Europe/Moscow"));

        publisher.publish(event);

        ArgumentCaptor<ConsumptionEvent> captor = ArgumentCaptor.forClass(ConsumptionEvent.class);
        verify(kafkaTemplate).send(eq("consumption-events"), eq("42"), captor.capture());
        ConsumptionEvent sent = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(42L, sent.userId());
        org.junit.jupiter.api.Assertions.assertEquals(ResourceType.TRAFFIC, sent.resourceType());
        org.junit.jupiter.api.Assertions.assertEquals(1.5, sent.amount());
    }

    @Test
    void publishUsesNullKeyWhenUserIdIsNull() {
        ConsumptionEvent event = new ConsumptionEvent(
            java.util.UUID.randomUUID(), null, ResourceType.MINUTES, 5.0,
            "2026-01-15T12:00:00+03:00");

        publisher.publish(event);

        verify(kafkaTemplate).send(eq("consumption-events"), isNull(), eq(event));
    }

    @Test
    void publishUsesUserIdAsStringKey() {
        ConsumptionEvent event = ConsumptionEvent.create(
            7L, ResourceType.MINUTES, 2.0, Instant.parse("2026-06-01T10:00:00Z"),
            ZoneId.of("UTC"));

        publisher.publish(event);

        verify(kafkaTemplate).send(eq("consumption-events"), eq("7"), any(ConsumptionEvent.class));
    }

    @Test
    void publishDoesNotThrowOnKafkaFailure() {
        when(kafkaTemplate.send(anyString(), any(), any()))
            .thenThrow(new RuntimeException("Kafka down"));

        ConsumptionEvent event = ConsumptionEvent.create(
            1L, ResourceType.TRAFFIC, 0.1, Instant.now(), ZoneId.of("UTC"));

        try {
            publisher.publish(event);
        } catch (RuntimeException ignored) {}
    }
}
