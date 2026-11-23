package ru.tmobile.generator.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import ru.tmobile.generator.config.GeneratorBackgroundProperties;
import ru.tmobile.generator.exception.GenerationAlreadyStartedException;
import ru.tmobile.generator.model.ConsumptionEvent;
import ru.tmobile.generator.model.GenerationMode;
import ru.tmobile.generator.model.ProfileType;
import ru.tmobile.generator.model.ResourceType;
import ru.tmobile.generator.profile.TripProfilePlanner;

class BackgroundGenerationServiceTest {

    private VirtualClockService virtualClock;
    private ConsumptionEventPublisher publisher;
    private GeneratorBackgroundProperties properties;
    private TripProfilePlanner tripPlanner;
    private BackgroundGenerationService service;

    @BeforeEach
    void setUp() {
        virtualClock = mock(VirtualClockService.class);
        publisher = mock(ConsumptionEventPublisher.class);
        properties = new GeneratorBackgroundProperties();
        properties.setTrafficBaseAmountInGB(0.1);
        properties.setMinutesBaseAmount(20.0);
        tripPlanner = mock(TripProfilePlanner.class);

        service = new BackgroundGenerationService(virtualClock, publisher, properties, tripPlanner);
        ReflectionTestUtils.setField(service, "threadPoolSize", 2);
        ReflectionTestUtils.setField(service, "systemZoneId", ZoneId.of("UTC"));
        service.init();
    }

    @AfterEach
    void tearDown() {
        service.stopAllBackgroundGeneration();
        service.shutdown();
    }

    @Test
    void startThrowsWhenSameTaskAlreadyRunning() {
        Instant t0 = Instant.parse("2026-01-15T12:00:00Z");
        when(virtualClock.instant()).thenReturn(t0);
        when(virtualClock.isPaused()).thenReturn(false);

        service.startBackgroundGeneration(1L, ResourceType.TRAFFIC, GenerationMode.SIMPLE, ProfileType.STANDARD, 1);

        assertThrows(GenerationAlreadyStartedException.class, () ->
            service.startBackgroundGeneration(1L, ResourceType.TRAFFIC, GenerationMode.SIMPLE, ProfileType.STANDARD, 1));
    }

    @Test
    void startAllowsDifferentResourceTypeForSameUser() {
        Instant t0 = Instant.parse("2026-01-15T12:00:00Z");
        when(virtualClock.instant()).thenReturn(t0);
        when(virtualClock.isPaused()).thenReturn(false);

        service.startBackgroundGeneration(1L, ResourceType.TRAFFIC, GenerationMode.SIMPLE, ProfileType.STANDARD, 1);

        assertDoesNotThrow(() ->
            service.startBackgroundGeneration(1L, ResourceType.MINUTES, GenerationMode.SIMPLE, ProfileType.STANDARD, 1));
    }

    @Test
    void tripProfileRejectsMinutesResource() {
        Instant t0 = Instant.parse("2026-01-15T12:00:00Z");
        when(virtualClock.instant()).thenReturn(t0);

        assertThrows(IllegalArgumentException.class, () ->
            service.startBackgroundGeneration(1L, ResourceType.MINUTES, GenerationMode.REALISTIC, ProfileType.TRIP, 1));
    }

    @Test
    void stopIsIdempotentForUnknownTask() {
        assertDoesNotThrow(() -> service.stopBackgroundGeneration(999L, ResourceType.TRAFFIC));
    }

    @Test
    void stopRemovesTaskAndAllowsRestart() throws InterruptedException {
        Instant t0 = Instant.parse("2026-01-15T12:00:00Z");
        when(virtualClock.instant()).thenReturn(t0);
        when(virtualClock.isPaused()).thenReturn(false);

        service.startBackgroundGeneration(1L, ResourceType.TRAFFIC, GenerationMode.SIMPLE, ProfileType.STANDARD, 1);
        service.stopBackgroundGeneration(1L, ResourceType.TRAFFIC);
        Thread.sleep(50);

        assertDoesNotThrow(() ->
            service.startBackgroundGeneration(1L, ResourceType.TRAFFIC, GenerationMode.SIMPLE, ProfileType.STANDARD, 1));
    }

    @Test
    void pausedClockSkipsPublishing() throws InterruptedException {
        Instant t0 = Instant.parse("2026-01-15T12:00:00Z");
        when(virtualClock.instant()).thenReturn(t0);
        when(virtualClock.isPaused()).thenReturn(true);

        service.startBackgroundGeneration(1L, ResourceType.TRAFFIC, GenerationMode.SIMPLE, ProfileType.STANDARD, 1);
        Thread.sleep(200);

        verify(publisher, never()).publish(any(ConsumptionEvent.class));
    }

    @Test
    void accumulatesAndPublishesWhenKafkaIntervalElapsed() throws InterruptedException {
        Instant t0 = Instant.parse("2026-01-15T12:00:00Z");
        Instant t1 = t0.plusSeconds(70);
        AtomicReference<Instant> currentTime = new AtomicReference<>(t0);
        when(virtualClock.instant()).thenAnswer(inv -> currentTime.get());
        when(virtualClock.isPaused()).thenReturn(false);

        service.startBackgroundGeneration(1L, ResourceType.TRAFFIC, GenerationMode.SIMPLE, ProfileType.STANDARD, 1);
        Thread.sleep(100);

        currentTime.set(t1);
        Thread.sleep(300);

        ArgumentCaptor<ConsumptionEvent> captor = ArgumentCaptor.forClass(ConsumptionEvent.class);
        verify(publisher, atLeastOnce()).publish(captor.capture());

        ConsumptionEvent event = captor.getAllValues().get(0);
        assertEquals(1L, event.userId());
        assertEquals(ResourceType.TRAFFIC, event.resourceType());
        assertTrue(event.amount() > 0, "amount should be positive, got " + event.amount());
    }

    @Test
    void stopAllClearsAllTasksAndAnomalies() throws InterruptedException {
        Instant t0 = Instant.parse("2026-01-15T12:00:00Z");
        when(virtualClock.instant()).thenReturn(t0);
        when(virtualClock.isPaused()).thenReturn(true);

        service.startBackgroundGeneration(1L, ResourceType.TRAFFIC, GenerationMode.SIMPLE, ProfileType.STANDARD, 1);
        service.startBackgroundGeneration(2L, ResourceType.MINUTES, GenerationMode.SIMPLE, ProfileType.STANDARD, 1);

        assertEquals(2, service.getActiveTasksStatus().size());

        service.stopAllBackgroundGeneration();

        assertEquals(0, service.getActiveTasksStatus().size());
    }

    @Test
    void startAnomalyThrowsWhenAlreadyRunning() {
        Instant t0 = Instant.parse("2026-01-15T12:00:00Z");
        when(virtualClock.instant()).thenReturn(t0);

        service.startAnomaly(1L, ResourceType.TRAFFIC);

        assertThrows(GenerationAlreadyStartedException.class, () ->
            service.startAnomaly(1L, ResourceType.TRAFFIC));
    }

    @Test
    void stopAnomalyIsIdempotentForUnknownUser() {
        assertDoesNotThrow(() -> service.stopAnomaly(999L));
    }

    @Test
    void activeTasksStatusIncludesBackgroundAndAnomalyTasks() {
        Instant t0 = Instant.parse("2026-01-15T12:00:00Z");
        when(virtualClock.instant()).thenReturn(t0);
        when(virtualClock.isPaused()).thenReturn(false);

        service.startBackgroundGeneration(1L, ResourceType.TRAFFIC, GenerationMode.SIMPLE, ProfileType.STANDARD, 10);
        service.startAnomaly(2L, ResourceType.MINUTES);

        var statuses = service.getActiveTasksStatus();
        assertEquals(2, statuses.size());

        long background = statuses.stream().filter(s -> "BACKGROUND".equals(s.taskType())).count();
        long anomaly = statuses.stream().filter(s -> "ANOMALY".equals(s.taskType())).count();
        assertEquals(1, background);
        assertEquals(1, anomaly);
    }

    @Test
    void anomalyPublishesEventsOverTime() throws InterruptedException {
        Instant t0 = Instant.parse("2026-01-15T12:00:00Z");
        AtomicLong virtualMillis = new AtomicLong(t0.toEpochMilli());
        when(virtualClock.instant()).thenAnswer(inv -> Instant.ofEpochMilli(virtualMillis.get()));
        when(virtualClock.isPaused()).thenReturn(false);

        service.startAnomaly(1L, ResourceType.TRAFFIC);
        Thread.sleep(100);

        virtualMillis.addAndGet(300_000L);
        Thread.sleep(300);

        ArgumentCaptor<ConsumptionEvent> captor = ArgumentCaptor.forClass(ConsumptionEvent.class);
        verify(publisher, atLeastOnce()).publish(captor.capture());

        for (ConsumptionEvent e : captor.getAllValues()) {
            assertEquals(1L, e.userId());
            assertEquals(ResourceType.TRAFFIC, e.resourceType());
            assertTrue(e.amount() > 0);
        }
    }

    @Test
    void shutdownTerminatesExecutorGracefully() {
        service.shutdown();
        assertDoesNotThrow(() -> service.shutdown());
    }
}
