package ru.tmobile.generator.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import ru.tmobile.generator.service.VirtualClockService.TimeChangeEvent;

class VirtualClockServiceTest {

    private KafkaTemplate<String, Object> kafkaTemplate;
    private VirtualClockService clock;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), any(), any())).thenReturn(mock(java.util.concurrent.CompletableFuture.class));
        clock = new VirtualClockService(kafkaTemplate, "Europe/Moscow");
    }

    @Test
    void defaultStateRunsAtRealTime() {
        assertFalse(clock.isPaused());
        assertEquals(1, clock.getMultiplier());
        long before = System.currentTimeMillis();
        long actual = clock.instant().toEpochMilli();
        long after = System.currentTimeMillis();
        assertTrue(actual >= before - 50 && actual <= after + 50,
            "virtual time should be close to real time, actual=" + actual);
    }

    @Test
    void pauseStopsVirtualTimeProgress() throws InterruptedException {
        clock.pause();
        assertTrue(clock.isPaused());
        Instant first = clock.instant();
        Thread.sleep(100);
        Instant second = clock.instant();
        assertEquals(first, second, "paused clock should not advance");
        verify(kafkaTemplate, atLeastOnce()).send(eq("time-change"), isNull(), any(TimeChangeEvent.class));
    }

    @Test
    void pauseIsIdempotent() {
        clock.pause();
        clock.pause();
        clock.pause();
        verify(kafkaTemplate, times(1)).send(eq("time-change"), isNull(), any(TimeChangeEvent.class));
    }

    @Test
    void resumeContinuesFromPausedVirtualTime() throws InterruptedException {
        clock.pause();
        Instant pausedTime = clock.instant();
        Thread.sleep(50);
        clock.resume();
        assertFalse(clock.isPaused());
        Instant resumed = clock.instant();
        long diff = resumed.toEpochMilli() - pausedTime.toEpochMilli();
        assertTrue(diff >= 0 && diff < 500,
            "resumed time should be close to paused time, diff=" + diff);
    }

    @Test
    void resumeIsIdempotentWhenNotPaused() {
        clock.resume();
        verify(kafkaTemplate, never()).send(anyString(), any(), any());
    }

    @Test
    void accelerateChangesMultiplier() {
        clock.accelerate(5);
        assertEquals(5, clock.getMultiplier());
        verify(kafkaTemplate, atLeastOnce()).send(eq("time-change"), isNull(), any(TimeChangeEvent.class));
    }

    @Test
    void accelerateAdvancesVirtualTimeFaster() throws InterruptedException {
        clock.accelerate(10);
        long t1 = clock.instant().toEpochMilli();
        Thread.sleep(100);
        long t2 = clock.instant().toEpochMilli();
        long elapsed = t2 - t1;
        assertTrue(elapsed >= 800 && elapsed <= 1500,
            "accelerated clock should advance ~10x, elapsed=" + elapsed);
    }

    @Test
    void resetRestoresDefaults() {
        clock.accelerate(5);
        clock.pause();
        clock.reset();
        assertFalse(clock.isPaused());
        assertEquals(1, clock.getMultiplier());
        long before = System.currentTimeMillis();
        long actual = clock.instant().toEpochMilli();
        long after = System.currentTimeMillis();
        assertTrue(actual >= before - 50 && actual <= after + 50);
    }

    @Test
    void timeChangeEventContainsCorrectFields() {
        clock.accelerate(3);
        ArgumentCaptor<TimeChangeEvent> captor = ArgumentCaptor.forClass(TimeChangeEvent.class);
        verify(kafkaTemplate, atLeastOnce()).send(eq("time-change"), isNull(), captor.capture());
        TimeChangeEvent event = captor.getValue();
        assertNotNull(event.event_id());
        assertFalse(event.event_id().isEmpty());
        assertEquals(3, event.multiplier());
        assertFalse(event.is_paused());
        assertNotNull(event.base_real_time());
        assertNotNull(event.base_virtual_time());
        assertNotNull(event.changed_at());
    }

    @Test
    void pauseEventHasIsPausedTrue() {
        clock.pause();
        ArgumentCaptor<TimeChangeEvent> captor = ArgumentCaptor.forClass(TimeChangeEvent.class);
        verify(kafkaTemplate).send(eq("time-change"), isNull(), captor.capture());
        TimeChangeEvent event = captor.getValue();
        assertTrue(event.is_paused());
    }

    @Test
    void getZoneReturnsConfiguredZone() {
        assertEquals(ZoneId.of("Europe/Moscow"), clock.getZone());
    }

    @Test
    void withZoneThrowsUnsupported() {
        assertThrows(UnsupportedOperationException.class,
            () -> clock.withZone(ZoneId.of("UTC")));
    }

    @Test
    void getRealTimeReturnsCloseToSystemTime() {
        long before = System.currentTimeMillis();
        long real = clock.getRealTime().toEpochMilli();
        long after = System.currentTimeMillis();
        assertTrue(real >= before && real <= after);
    }

    @Test
    void publishTimeChangeFailureDoesNotThrow() {
        when(kafkaTemplate.send(anyString(), any(), any()))
            .thenThrow(new RuntimeException("Kafka unavailable"));
        assertDoesNotThrow(() -> clock.accelerate(2));
    }
}
