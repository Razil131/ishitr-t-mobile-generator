package ru.tmobile.generator.model;

import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class GeneratorApiModels {

    private GeneratorApiModels() {}

    public record StartUserGenerationRequest(
        @NotNull @JsonProperty("user_id") Long userId,
        @NotNull @JsonProperty("resource_type") ResourceType resourceType,
        @NotNull @JsonProperty("mode") GenerationMode mode,
        @JsonProperty("profile_type") ProfileType profileType,
        @JsonProperty("interval_seconds") Integer intervalSeconds
    ) {}

    public record StopUserGenerationRequest(
        @NotNull @JsonProperty("user_id") Long userId,
        @NotNull @JsonProperty("resource_type") ResourceType resourceType
    ) {}

    public record StartAllGenerationRequest(
        @NotNull @JsonProperty("resource_type") ResourceType resourceType,
        @NotNull @JsonProperty("mode") GenerationMode mode,
        @JsonProperty("profile_type") ProfileType profileType,
        @JsonProperty("interval_seconds") Integer intervalSeconds
    ) {}

    public record GenerationStatusResponse(
        @JsonProperty("active_tasks") java.util.List<TaskInfo> activeTasks
    ) {}

    public record TaskInfo(
        @JsonProperty("user_id") Long userId,
        @JsonProperty("resource_type") ResourceType resourceType,
        @JsonProperty("mode") GenerationMode mode,
        @JsonProperty("profile_type") ProfileType profileType,
        @JsonProperty("interval_seconds") int intervalSeconds,
        @JsonProperty("task_type") String taskType
    ) {}

    public record InstantDeductRequest(
        @NotNull @JsonProperty("user_id") Long userId,
        @NotNull @JsonProperty("resource_type") ResourceType resourceType,
        @NotNull @JsonProperty("amount") Double amount
    ) {}

    public record AnomalyStartRequest(
        @NotNull @JsonProperty("user_id") Long userId,
        @NotNull @JsonProperty("resource_type") ResourceType resourceType
    ) {}

    public record AnomalyStopRequest(
        @NotNull @JsonProperty("user_id") Long userId
    ) {}

    public record DelayedEventRequest(
        @NotNull @JsonProperty("user_id") Long userId,
        @NotNull @JsonProperty("resource_type") ResourceType resourceType,
        @NotNull @JsonProperty("amount") Double amount,
        @NotNull @JsonProperty("hours_ago") Integer hoursAgo
    ) {}
}