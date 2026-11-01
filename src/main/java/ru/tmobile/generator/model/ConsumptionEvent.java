package ru.tmobile.generator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public record ConsumptionEvent(
    @JsonProperty("event_id")
    UUID eventId,

    @JsonProperty("user_id")
    Long userId,

    @JsonProperty("resource_type")
    ResourceType resourceType,

    @JsonProperty("amount")
    double amount,

    @JsonProperty("event_timestamp")
    String eventTimestamp,

    @JsonProperty("is_anomaly")
    boolean isAnomaly)
{
    public static ConsumptionEvent create(
        Long userId,
        ResourceType resourceType,
        double amount,
        Instant timestamp,
        ZoneId zoneId,
        boolean isAnomaly
    ) {
        return new ConsumptionEvent(
            UUID.randomUUID(),
            userId,
            resourceType,
            amount,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                timestamp.atZone(zoneId)
            ),
            isAnomaly
        );
    }
}