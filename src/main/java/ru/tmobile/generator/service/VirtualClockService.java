package ru.tmobile.generator.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class VirtualClockService extends Clock{
    private static final Logger log = LoggerFactory.getLogger(VirtualClockService.class);
    private static final String TIME_CHANGE_TOPIC = "time-change";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ZoneId timeZone;

    private long realStartTime = System.currentTimeMillis();
    private long virtualStartTime = System.currentTimeMillis();
    private int multiplier = 1;
    private boolean isPaused = false;

    private long pauseRealTime = 0;
    private long pauseVirtualTime = 0;

    public VirtualClockService(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${generator.timezone:Europe/Moscow}") String timeZoneId
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.timeZone = ZoneId.of(timeZoneId);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(){
        try {
            publishTimeChange();
            log.info("Initial time change event published");
        } catch (Exception e) {
            log.warn("Failed to publish initial time change event: {}", e.getMessage());
        }
    }

    public synchronized void accelerate(int newMultiplier){
        this.multiplier = newMultiplier;
        if (!isPaused) {
            this.virtualStartTime = getCurrentVirtualTime();
            this.realStartTime = System.currentTimeMillis();
        }
        publishTimeChange();
    }

    public synchronized void pause(){
        if (!isPaused) {
            this.pauseRealTime = System.currentTimeMillis();
            this.pauseVirtualTime = getCurrentVirtualTime();
            this.isPaused = true;
            publishTimeChange();
        }
    }

    public synchronized void resume(){
        if (isPaused) {
            this.isPaused = false;
            long pausedDuration = System.currentTimeMillis() - this.pauseRealTime;
            this.realStartTime += pausedDuration;
            publishTimeChange();
        }
    }

    public synchronized void reset(){
        this.realStartTime = System.currentTimeMillis();
        this.virtualStartTime = System.currentTimeMillis();
        this.multiplier = 1;
        this.isPaused = false;
        publishTimeChange();
    }

    private synchronized void publishTimeChange(){
        long currentVirtualTime = getCurrentVirtualTime();
        long currentRealTime = System.currentTimeMillis();
        
        TimeChangeEvent event = new TimeChangeEvent(
            UUID.randomUUID().toString(),
            formatTime(currentRealTime),
            formatTime(currentVirtualTime),
            multiplier,
            isPaused,
            formatTime(currentVirtualTime)
        );
        
        kafkaTemplate.send(TIME_CHANGE_TOPIC, null, event).whenComplete((result, ex) -> {
            if (ex == null){
                log.info("[Kafka] TimeChangeEvent published: {}", event);
            } else {
                log.error("[Kafka] Failed to publish TimeChangeEvent", ex);
            }
        });
    }

    private String formatTime(long epochMillis){
        return Instant.ofEpochMilli(epochMillis)
                .atZone(timeZone)
                .format(FORMATTER);
    }

    private synchronized long getCurrentVirtualTime(){
        if (isPaused) return pauseVirtualTime;
        long realElapsed = System.currentTimeMillis() - realStartTime;
        return virtualStartTime + (realElapsed * multiplier);
    }

    @Override
    public ZoneId getZone(){
        return timeZone;
    }

    @Override
    public Clock withZone(ZoneId zone){
        throw new UnsupportedOperationException("VirtualClock does not support withZone");
    }

    @Override
    public synchronized Instant instant(){
        return Instant.ofEpochMilli(getCurrentVirtualTime());
    }

    public synchronized int getMultiplier(){
        return multiplier;
    }

    public synchronized boolean isPaused(){
        return isPaused;
    }

    public synchronized Instant getRealTime(){
        return Instant.ofEpochMilli(System.currentTimeMillis());
    }

    public record TimeChangeEvent(
        String event_id,
        String base_real_time,
        String base_virtual_time,
        int multiplier,
        boolean is_paused,
        String changed_at
    ) {}
}