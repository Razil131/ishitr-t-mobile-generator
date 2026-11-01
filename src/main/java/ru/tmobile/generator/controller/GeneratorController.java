package ru.tmobile.generator.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.tmobile.generator.client.BackendUsersClient;
import ru.tmobile.generator.model.GenerationMode;
import ru.tmobile.generator.model.GeneratorApiModels.*;
import ru.tmobile.generator.model.ProfileType;
import ru.tmobile.generator.model.ResourceType;
import ru.tmobile.generator.service.BackgroundGenerationService;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/generator/events/background")
@RequiredArgsConstructor
public class GeneratorController {

    private final BackgroundGenerationService backgroundGenerationService;
    private final BackendUsersClient backendUsersClient;

    @PostMapping("/user/start")
    public ResponseEntity<Map<String, String>> startUserGeneration(
            @Valid @RequestBody StartUserGenerationRequest request
    ) {
        log.info("Starting generation for user {} type {}", request.userId(), request.resourceType());
        
        ProfileType profile = request.profileType() != null ? request.profileType() : ProfileType.STANDARD;
        int interval = request.intervalSeconds() != null ? request.intervalSeconds() : 60;

        backgroundGenerationService.startBackgroundGeneration(
                request.userId(),
                request.resourceType(),
                request.mode(),
                profile,
                interval
        );

        return ResponseEntity.ok(Map.of(
                "status", "started",
                "user_id", request.userId().toString(),
                "resource_type", request.resourceType().name()
        ));
    }

    @PostMapping("/user/stop")
    public ResponseEntity<Map<String, String>> stopUserGeneration(
            @Valid @RequestBody StopUserGenerationRequest request
    ) {
        log.info("Stopping generation for user {} type {}", request.userId(), request.resourceType());
        
        backgroundGenerationService.stopBackgroundGeneration(request.userId(), request.resourceType());

        return ResponseEntity.ok(Map.of(
                "status", "stopped",
                "user_id", request.userId().toString(),
                "resource_type", request.resourceType().name()
        ));
    }

    @PostMapping("/all/start")
    public ResponseEntity<Map<String, Object>> startAllGeneration(
            @Valid @RequestBody StartAllGenerationRequest request
    ) {
        log.info("Starting mass generation for all users, type {}", request.resourceType());

        List<Long> userIds = backendUsersClient.getActiveUserIds();
        if (userIds.isEmpty()) {
            log.warn("No active users found in Backend");
            return ResponseEntity.ok(Map.of(
                    "total_active_users", 0,
                    "started_count", 0,
                    "ignored_count", 0,
                    "message", "No active users found to start generation"
            ));
        }

        ProfileType profile = request.profileType() != null ? request.profileType() : ProfileType.STANDARD;
        int interval = request.intervalSeconds() != null ? request.intervalSeconds() : 60;

        int startedCount = 0;
        int ignoredCount = 0;

        for (Long userId : userIds) {
            try {
                backgroundGenerationService.startBackgroundGeneration(
                        userId,
                        request.resourceType(),
                        request.mode(),
                        profile,
                        interval
                );
                startedCount++;
            } catch (Exception e) {
                ignoredCount++;
                log.debug("Ignored user {}: {}", userId, e.getMessage());
            }
        }

        String message = String.format("Массовая генерация запущена для %d пользователей. %d пользователей проигнорированы (уже активны).", startedCount, ignoredCount);

        return ResponseEntity.ok(Map.of(
                "total_active_users", userIds.size(),
                "started_count", startedCount,
                "ignored_count", ignoredCount,
                "message", message
        ));
    }

    @PostMapping("/all/stop")
    public ResponseEntity<Map<String, String>> stopAllGeneration() {
        log.info("Stopping all background generation");
        backgroundGenerationService.stopAllBackgroundGeneration();
        return ResponseEntity.ok(Map.of("status", "all_stopped"));
    }

    @GetMapping("/status")
    public ResponseEntity<GenerationStatusResponse> getStatus() {
        var tasks = backgroundGenerationService.getActiveTasksStatus().stream()
                .map(t -> new TaskInfo(
                        t.userId(),
                        t.resourceType(),
                        t.mode(),
                        t.profileType(),
                        t.intervalSeconds(),
                        t.taskType()
                ))
                .toList();

        return ResponseEntity.ok(new GenerationStatusResponse(tasks));
    }
}