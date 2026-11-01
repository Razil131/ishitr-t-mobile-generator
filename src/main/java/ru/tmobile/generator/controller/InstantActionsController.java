package ru.tmobile.generator.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.tmobile.generator.model.ConsumptionEvent;
import ru.tmobile.generator.model.GeneratorApiModels.*;
import ru.tmobile.generator.service.BackgroundGenerationService;
import ru.tmobile.generator.service.ConsumptionEventPublisher;
import ru.tmobile.generator.service.VirtualClockService;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/generator/events")
public class InstantActionsController {

    private final ConsumptionEventPublisher publisher;
    private final VirtualClockService virtualClock;
    private final BackgroundGenerationService backgroundGenerationService;
    private final ZoneId systemZoneId;

    public InstantActionsController(
            ConsumptionEventPublisher publisher,
            VirtualClockService virtualClock,
            BackgroundGenerationService backgroundGenerationService,
            @Value("${generator.timezone:Europe/Moscow}") String timezone
    ) {
        this.publisher = publisher;
        this.virtualClock = virtualClock;
        this.backgroundGenerationService = backgroundGenerationService;
        this.systemZoneId = ZoneId.of(timezone);
    }

    @PostMapping("/instant-deduct")
    public ResponseEntity<Map<String, Object>> instantDeduct(
            @Valid @RequestBody InstantDeductRequest request
    ) {
        log.info("Instant deduct for user {} type {} amount {}",
                request.userId(), request.resourceType(), request.amount());

        Instant now = virtualClock.instant();
        ConsumptionEvent event = ConsumptionEvent.create(
                request.userId(),
                request.resourceType(),
                request.amount(),
                now,
                systemZoneId,
                false
        );
        publisher.publish(event);

        return ResponseEntity.ok(Map.of(
                "status", "published",
                "event_id", event.eventId().toString(),
                "user_id", request.userId(),
                "resource_type", request.resourceType().name(),
                "amount", request.amount(),
                "event_timestamp", event.eventTimestamp()
        ));
    }

    @PostMapping("/anomaly/user/start")
    public ResponseEntity<Map<String, Object>> startAnomaly(
            @Valid @RequestBody AnomalyStartRequest request
    ) {
        log.info("Starting anomaly for user {} type {}", request.userId(), request.resourceType());

        backgroundGenerationService.startAnomaly(request.userId(), request.resourceType());

        return ResponseEntity.ok(Map.of(
                "status", "anomaly_started",
                "user_id", request.userId(),
                "resource_type", request.resourceType().name()
        ));
    }

    @PostMapping("/anomaly/user/stop")
    public ResponseEntity<Map<String, Object>> stopAnomaly(
            @Valid @RequestBody AnomalyStopRequest request
    ) {
        log.info("Stopping anomaly for user {}", request.userId());

        backgroundGenerationService.stopAnomaly(request.userId());

        return ResponseEntity.ok(Map.of(
                "status", "anomaly_stopped",
                "user_id", request.userId()
        ));
    }

    @PostMapping("/delayed")
    public ResponseEntity<Map<String, Object>> sendDelayedEvent(
            @Valid @RequestBody DelayedEventRequest request
    ) {
        log.info("Delayed event for user {} type {} amount {} hoursAgo {}",
                request.userId(), request.resourceType(), request.amount(), request.hoursAgo());

        Instant delayedTime = virtualClock.instant()
                .minusSeconds((long) request.hoursAgo() * 3600L);

        ConsumptionEvent event = ConsumptionEvent.create(
                request.userId(),
                request.resourceType(),
                request.amount(),
                delayedTime,
                systemZoneId,
                false
        );
        publisher.publish(event);

        return ResponseEntity.ok(Map.of(
                "status", "published",
                "event_id", event.eventId().toString(),
                "user_id", request.userId(),
                "resource_type", request.resourceType().name(),
                "amount", request.amount(),
                "event_timestamp", event.eventTimestamp(),
                "hours_ago", request.hoursAgo()
        ));
    }
}