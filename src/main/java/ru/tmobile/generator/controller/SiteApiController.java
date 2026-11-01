package ru.tmobile.generator.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.tmobile.generator.client.BackendUsersClient;
import ru.tmobile.generator.model.*;
import ru.tmobile.generator.service.BackgroundGenerationService;
import ru.tmobile.generator.service.ConsumptionEventPublisher;
import ru.tmobile.generator.service.VirtualClockService;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SiteApiController {

    private final VirtualClockService virtualClock;
    private final BackgroundGenerationService backgroundGenerationService;
    private final ConsumptionEventPublisher publisher;
    private final BackendUsersClient backendUsersClient;

    @Value("${generator.timezone:Europe/Moscow}")
    private ZoneId timeZone;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final AtomicInteger generationIdCounter = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, ActiveGenInfo> activeGenerations = new ConcurrentHashMap<>();

    @GetMapping("/time/status")
    public ResponseEntity<Map<String, Object>> getTimeStatus() {
        Instant realTime = virtualClock.getRealTime();
        Instant virtualTime = virtualClock.instant();
        int multiplier = virtualClock.getMultiplier();
        boolean isPaused = virtualClock.isPaused();

        return ResponseEntity.ok(Map.of(
                "realTime", formatInstant(realTime),
                "virtualTime", formatInstant(virtualTime),
                "multiplier", multiplier,
                "isPaused", isPaused,
                "isAccelerated", multiplier > 1,
                "activeThreads", backgroundGenerationService.getActiveTasksStatus().size(),
                "lastEvent", ""
        ));
    }

    @PostMapping("/time/multiplier")
    public ResponseEntity<Map<String, Object>> setMultiplier(@RequestBody Map<String, Integer> body) {
        Integer multiplier = body.get("multiplier");
        if (multiplier == null || multiplier < 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "multiplier must be >= 1"));
        }
        virtualClock.accelerate(multiplier);
        return ResponseEntity.ok(Map.of("multiplier", multiplier));
    }

    @PostMapping("/time/pause")
    public ResponseEntity<Map<String, Object>> pauseTime() {
        virtualClock.pause();
        return ResponseEntity.ok(Map.of("isPaused", true));
    }

    @PostMapping("/time/resume")
    public ResponseEntity<Map<String, Object>> resumeTime() {
        virtualClock.resume();
        return ResponseEntity.ok(Map.of("isPaused", false));
    }

    @PostMapping("/time/reset")
    public ResponseEntity<Map<String, Object>> resetTime() {
        virtualClock.reset();
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getUsers() {
        List<Long> userIds = backendUsersClient.getActiveUserIds();
        List<Map<String, Object>> users = userIds.stream()
                .map(id -> Map.<String, Object>of(
                        "id", id,
                        "phone", "+7 (999) 000-00-" + String.format("%02d", id),
                        "name", "User " + id,
                        "groupId", 1,
                        "groupName", "Default Group",
                        "role", "member",
                        "status", "active"
                ))
                .toList();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/groups")
    public ResponseEntity<List<Map<String, Object>>> getGroups() {
        List<Map<String, Object>> groups = List.of(
                Map.of(
                        "id", 1,
                        "name", "Default Group",
                        "ownerId", 1,
                        "ownerName", "User 1",
                        "status", "Активна",
                        "membersCount", backendUsersClient.getActiveUserIds().size(),
                        "maxMembers", 10,
                        "created", "01.01.2026"
                )
        );
        return ResponseEntity.ok(groups);
    }

    @GetMapping("/tariffs")
    public ResponseEntity<List<Map<String, Object>>> getTariffs() {
        List<Map<String, Object>> tariffs = List.of(
                Map.of("id", 1, "name", "Базовый", "traffic", 30, "minutes", 600),
                Map.of("id", 2, "name", "Семейный", "traffic", 50, "minutes", 500),
                Map.of("id", 3, "name", "Максимум", "traffic", 100, "minutes", 1200)
        );
        return ResponseEntity.ok(tariffs);
    }

    @PostMapping("/tariffs")
    public ResponseEntity<Map<String, Object>> createTariff(@RequestBody Map<String, Object> body) {
        Map<String, Object> tariff = new java.util.HashMap<>(body);
        tariff.put("id", (int) (Math.random() * 1000) + 10);
        return ResponseEntity.ok(tariff);
    }

    @DeleteMapping("/tariffs/{id}")
    public ResponseEntity<Void> deleteTariff(@PathVariable int id) {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/events/instant-deduct")
    public ResponseEntity<Map<String, Object>> instantDeduct(@RequestBody Map<String, Object> body) {
        Long userId = toLong(body.get("userId"));
        Double amount = toDouble(body.get("amount"));
        String resourceTypeStr = (String) body.get("resourceType");
        ResourceType resourceType = ResourceType.valueOf(resourceTypeStr);

        ConsumptionEvent event = ConsumptionEvent.create(
                userId, resourceType, amount,
                virtualClock.instant(), timeZone, false
        );
        publisher.publish(event);

        log.info("Instant deduct: user={}, amount={}, type={}", userId, amount, resourceTypeStr);
        return ResponseEntity.ok(Map.of("success", true, "eventId", event.eventId().toString()));
    }

    @PostMapping("/events/anomaly")
    public ResponseEntity<Map<String, Object>> startAnomaly(@RequestBody Map<String, Object> body) {
        Long userId = toLong(body.get("userId"));
        Double amount = toDouble(body.get("amount"));
        Double durationHours = toDouble(body.get("durationHours"));

        ConsumptionEvent event = ConsumptionEvent.create(
                userId, ResourceType.TRAFFIC, amount,
                virtualClock.instant(), timeZone, true
        );
        publisher.publish(event);

        int id = generationIdCounter.incrementAndGet();
        activeGenerations.put(id, new ActiveGenInfo(
                id, "User " + userId, "user", "Аномалия",
                "TRAFFIC " + amount + "GB (" + durationHours + "h)",
                "Аномалия " + amount + "ГБ / " + durationHours + "ч",
                "Активна",
                java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        ));

        log.info("Anomaly started: user={}, amount={}GB, duration={}h", userId, amount, durationHours);
        return ResponseEntity.ok(activeGenerations.get(id).toMap());
    }

    @PostMapping("/events/delayed")
    public ResponseEntity<Map<String, Object>> sendDelayedEvent(@RequestBody Map<String, Object> body) {
        Long userId = toLong(body.get("userId"));
        Double amount = toDouble(body.get("amount"));
        Double hoursAgo = toDouble(body.get("hoursAgo"));

        Instant delayedTime = virtualClock.instant().minusSeconds((long) (hoursAgo * 3600));
        ConsumptionEvent event = ConsumptionEvent.create(
                userId, ResourceType.TRAFFIC, amount,
                delayedTime, timeZone, false
        );
        publisher.publish(event);

        log.info("Delayed event: user={}, amount={}, hoursAgo={}", userId, amount, hoursAgo);
        return ResponseEntity.ok(Map.of("success", true, "eventId", event.eventId().toString()));
    }

    @PostMapping("/events/bulk")
    public ResponseEntity<Map<String, Object>> bulkGenerate(@RequestBody Map<String, Object> body) {
        Integer count = toInt(body.get("count"));
        String resourceTypeStr = (String) body.get("resourceType");
        ResourceType resourceType = ResourceType.valueOf(resourceTypeStr);

        List<Long> userIds = backendUsersClient.getActiveUserIds();
        int generated = 0;
        for (Long userId : userIds) {
            for (int i = 0; i < count; i++) {
                double amt = resourceType == ResourceType.TRAFFIC
                        ? 0.1 + Math.random() * 2.0
                        : 1.0 + Math.random() * 10.0;
                publisher.publish(ConsumptionEvent.create(
                        userId, resourceType, amt,
                        virtualClock.instant(), timeZone, false
                ));
                generated++;
            }
        }

        log.info("Bulk generate: count={}, users={}, total={}", count, userIds.size(), generated);
        return ResponseEntity.ok(Map.of("success", true, "generatedCount", generated));
    }

    @PostMapping("/events/background-start")
    public ResponseEntity<Map<String, Object>> startBackgroundGeneration(@RequestBody Map<String, Object> body) {
        String targetMode = (String) body.get("targetMode");
        Long targetId = toLong(body.get("targetId"));
        String bgMode = (String) body.get("bgMode");

        GenerationMode mode = "realistic".equalsIgnoreCase(bgMode)
                ? GenerationMode.REALISTIC : GenerationMode.SIMPLE;
        ProfileType profile = ProfileType.STANDARD;
        int intervalSec = 5;

        if ("realistic".equalsIgnoreCase(bgMode)) {
            String profileStr = (String) body.getOrDefault("profile", "STANDARD");
            profile = ProfileType.valueOf(profileStr.toUpperCase());
            intervalSec = 60;
        } else {
            Integer intervalParam = toInt(body.get("intervalSec"));
            if (intervalParam != null && intervalParam > 0) {
                intervalSec = intervalParam;
            }
        }

        backgroundGenerationService.startBackgroundGeneration(
                targetId, ResourceType.TRAFFIC, mode, profile, intervalSec
        );

        int id = generationIdCounter.incrementAndGet();
        String modeLabel = mode == GenerationMode.SIMPLE
                ? "Простой (" + intervalSec + "с)"
                : (profile == ProfileType.TRIP ? "Поездка" : "Стандарт");

        activeGenerations.put(id, new ActiveGenInfo(
                id, "User " + targetId, targetMode, "Фоновая",
                mode.name(), modeLabel, "Активна",
                java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        ));

        log.info("Background gen started: id={}, user={}, mode={}", id, targetId, mode);
        return ResponseEntity.ok(activeGenerations.get(id).toMap());
    }

    @PostMapping("/events/background-stop/{id}")
    public ResponseEntity<Void> stopBackgroundGeneration(@PathVariable int id) {
        ActiveGenInfo info = activeGenerations.remove(id);
        if (info != null) {
            try {
                Long userId = Long.parseLong(info.target().replaceAll("[^0-9]", ""));
                backgroundGenerationService.stopBackgroundGeneration(userId, ResourceType.TRAFFIC);
            } catch (NumberFormatException e) {
                log.warn("Could not parse userId from target: {}", info.target());
            }
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/events/active")
    public ResponseEntity<List<Map<String, Object>>> getActiveGenerations() {
        List<Map<String, Object>> result = activeGenerations.values().stream()
                .map(ActiveGenInfo::toMap).toList();
        return ResponseEntity.ok(result);
    }

    private String formatInstant(Instant instant) {
        return instant.atZone(timeZone).format(FORMATTER);
    }

    private Long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) return Long.parseLong(s);
        return 0L;
    }

    private Double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) return Double.parseDouble(s);
        return 0.0;
    }

    private Integer toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) return Integer.parseInt(s);
        return 0;
    }

    private record ActiveGenInfo(
            int id, String target, String targetType, String type,
            String mode, String modeLabel, String status, String startedAt
    ) {
        Map<String, Object> toMap() {
            return Map.of(
                    "id", id, "target", target, "targetType", targetType,
                    "type", type, "mode", mode, "modeLabel", modeLabel,
                    "status", status, "startedAt", startedAt
            );
        }
    }
}