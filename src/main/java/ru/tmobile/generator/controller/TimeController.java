package ru.tmobile.generator.controller;

import ru.tmobile.generator.service.VirtualClockService;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/v1/generator/time")
@RequiredArgsConstructor
public class TimeController{
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    
    private final VirtualClockService virtualClock;
    
    @Value("${generator.timezone:Europe/Moscow}")
    private ZoneId timeZone;

    @GetMapping
    public TimeStatusResponse getStatus(){
        return new TimeStatusResponse(
            formatInstant(virtualClock.getRealTime()),
            formatInstant(virtualClock.instant()),
            virtualClock.getMultiplier(),
            virtualClock.isPaused()
        );
    }

    @PostMapping("/accelerate")
    public TimeStatusResponse accelerate(@Valid @RequestBody AccelerateRequest request) {
        virtualClock.accelerate(request.multiplier());
        return getStatus();
    }

    @PostMapping("/pause")
    public TimeStatusResponse pause(){
        virtualClock.pause();
        return getStatus();
    }

    @PostMapping("/resume")
    public TimeStatusResponse resume(){
        virtualClock.resume();
        return getStatus();
    }

    @PostMapping("/reset")
    public TimeStatusResponse reset(){
        virtualClock.reset();
        return getStatus();
    }
    
    private String formatInstant(Instant instant){
        return instant.atZone(timeZone).format(FORMATTER);
    }

    public record TimeStatusResponse(
        String real_time,
        String virtual_time,
        int multiplier,
        boolean is_paused
    ) {}

    public record AccelerateRequest(
        @NotNull(message = "multiplier is required")
        @Min(value = 1, message = "multiplier must be >= 1")
        @Max(value = 1_000_000, message = "multiplier must be <= 1_000_000")
        Integer multiplier
    ) {}
}