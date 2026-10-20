package ru.tmobile.generator.controller;

import ru.tmobile.generator.service.VirtualClockService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/v1/generator/time")
@RequiredArgsConstructor
public class TimeController {
    private final VirtualClockService virtualClock;

    @GetMapping
    public TimeStatusResponse getStatus(){
        return new TimeStatusResponse(
            virtualClock.getRealTime(),
            virtualClock.instant(),
            virtualClock.getMultiplier(),
            virtualClock.isPaused()
        );
    }

    @PostMapping("/accelerate")
    public TimeStatusResponse accelerate(@RequestBody AccelerateRequest request){
        virtualClock.accelerate(request.getMultiplier());
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

    @Data
    public static class TimeStatusResponse{
        @JsonProperty("real_time")
        private String real_time;

        @JsonProperty("virtual_time")
        private String virtual_time;


        private double multiplier;

        @JsonProperty("is_paused")
        private boolean paused;

        public TimeStatusResponse(Instant real, Instant virt, double mult, boolean paused){
            DateTimeFormatter fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
            this.real_time = real.atZone(java.time.ZoneOffset.UTC).format(fmt);
            this.virtual_time = virt.atZone(java.time.ZoneOffset.UTC).format(fmt);
            this.multiplier = mult;
            this.paused = paused;

        }
    }

    @Data
    public static class AccelerateRequest{
        private int multiplier;
    }
}
