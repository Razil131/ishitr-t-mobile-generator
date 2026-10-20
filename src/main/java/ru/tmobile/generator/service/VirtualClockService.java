package ru.tmobile.generator.service;

import org.springframework.kafka.core.KafkaTemplate;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

@Service
public class VirtualClockService extends Clock {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private long realStartTime = System.currentTimeMillis();
    private long virtualStartTime = System.currentTimeMillis();
    private double multiplier = 1.0;
    private boolean isPaused = false;

    private long pauseRealTime = 0;
    private long pauseVirtualTime = 0;

    public VirtualClockService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostConstruct
    public void init() {
        publishTimeUpdate();
    }

    public synchronized void accelerate(double newMultiplier){
        if (isPaused) return;
        this.virtualStartTime = getCurrentVirtualTime();
        this.realStartTime = System.currentTimeMillis();
        this.multiplier = newMultiplier;
        publishTimeUpdate();
    }

    public synchronized void pause(){
        if (!isPaused){
            this.pauseRealTime = System.currentTimeMillis();
            this.pauseVirtualTime = getCurrentVirtualTime();
            this.isPaused = true;
            publishTimeUpdate();
        }
    }

    public synchronized void resume(){
        if (isPaused){
            this.isPaused = false;
            long pausedDuration = System.currentTimeMillis() - this.pauseRealTime;
            this.realStartTime += pausedDuration;
            publishTimeUpdate();
        }
    }

    public synchronized void reset(){
        this.realStartTime = System.currentTimeMillis();
        this.virtualStartTime = System.currentTimeMillis();
        this.multiplier = 1.0;
        this.isPaused = false;
        publishTimeUpdate();
    }

    private void publishTimeUpdate() {
        TimeUpdateEvent event = new TimeUpdateEvent(
            virtualStartTime,
            realStartTime,
            multiplier,
            isPaused
        );
        
        kafkaTemplate.send("time-sync", event).whenComplete((result, ex) -> {
            if (ex == null) {
                System.out.println("[Kafka] success: " + event);
            } else {
                System.err.println("[Kafka] error: " + ex.getMessage());
            }
        });
    }

    private long getCurrentVirtualTime(){
        if (isPaused) return pauseVirtualTime;
        long realElapsed = System.currentTimeMillis() - realStartTime;
        return (long) (virtualStartTime + (realElapsed * multiplier));
    }

    @Override
    public ZoneId getZone(){
        return ZoneId.systemDefault();
    }

    @Override
    public Clock withZone(ZoneId zone){
        throw new UnsupportedOperationException("VirtualClock does not support withZone");
    }

    @Override
    public Instant instant(){
        return Instant.ofEpochMilli(getCurrentVirtualTime());
    }

    public double getMultiplier() { return multiplier; }
    public boolean isPaused() { return isPaused; }
    public Instant getRealTime() { return Instant.ofEpochMilli(System.currentTimeMillis()); }

    public record TimeUpdateEvent(
        long virtualStartTime,
        long realStartTime,
        double multiplier,
        boolean isPaused
    ) {}
}
