package ru.tmobile.generator.service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import ru.tmobile.generator.config.GeneratorBackgroundProperties;
import ru.tmobile.generator.exception.GenerationAlreadyStartedException;
import ru.tmobile.generator.exception.GenerationNotStartedException;
import ru.tmobile.generator.model.ConsumptionEvent;
import ru.tmobile.generator.model.GenerationMode;
import ru.tmobile.generator.model.ProfileType;
import ru.tmobile.generator.model.ResourceType;
import ru.tmobile.generator.profile.TripProfilePlanner;
import ru.tmobile.generator.profile.TripProfilePlanner.TripPlan;

@Service
public class BackgroundGenerationService {
    private static final Logger log = LoggerFactory.getLogger(BackgroundGenerationService.class);
    private final ConcurrentHashMap<TaskKey, TaskContext> activeTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AnomalyContext> activeAnomalies = new ConcurrentHashMap<>();
    private final VirtualClockService virtualClock;
    private final TripProfilePlanner tripPlanner;

    @Value("${generator.thread-pool-size:10}")
    private int threadPoolSize;
    private ScheduledExecutorService scheduledExecutorService;

    private final GeneratorBackgroundProperties properties;

    @Value("${generator.timezone:Europe/Moscow}")
    private ZoneId systemZoneId;

    private final ConsumptionEventPublisher publisher;

    private final int MAX_EVENTS_PER_TICK = 100;

    public BackgroundGenerationService(VirtualClockService virtualClock, ConsumptionEventPublisher publisher, GeneratorBackgroundProperties properties, TripProfilePlanner tripPlanner) {
        this.virtualClock = virtualClock;
        this.publisher = publisher;
        this.properties = properties;
        this.tripPlanner = tripPlanner;
    }

    @PostConstruct
    public void init() {
        scheduledExecutorService = Executors.newScheduledThreadPool(threadPoolSize);
    }

    @PreDestroy
    public void shutdown() {
        scheduledExecutorService.shutdown();
        try {
            if (!scheduledExecutorService.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduledExecutorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduledExecutorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void startBackgroundGeneration(Long userId, ResourceType resourceType, GenerationMode mode, ProfileType profileType, int intervalSeconds){
        TaskKey key = new TaskKey(userId, resourceType);
        if (activeTasks.containsKey(key)) {
            throw new GenerationAlreadyStartedException(userId, resourceType);
        }

        try{
            Instant now = virtualClock.instant();
            TripState tripState = null;
            if (mode == GenerationMode.REALISTIC && profileType == ProfileType.TRIP) {
                if (resourceType != ResourceType.TRAFFIC) {
                    throw new IllegalArgumentException(
                        "TRIP profile is only for traffic"
                    );
                }
                TripPlan plan = tripPlanner.plan(now, systemZoneId);
                tripState = new TripState(plan.events(), 0);
            }
            GenerationTask generationTask = new GenerationTask(
                userId, resourceType, mode, profileType, intervalSeconds, 
                0.0, now, now, tripState
            );
            Runnable task = createTask(userId, resourceType, mode, profileType, intervalSeconds);
            ScheduledFuture<?> future = scheduledExecutorService.scheduleAtFixedRate(task, 0, 50, TimeUnit.MILLISECONDS);
            activeTasks.put(key, new TaskContext(generationTask, future));
        } catch (Exception e){
            activeTasks.remove(key);
            throw e;
        }
    }

    public void stopBackgroundGeneration(Long userId, ResourceType resourceType) {
        TaskKey key = new TaskKey(userId, resourceType);
        TaskContext context = activeTasks.remove(key);

        if (context == null) {
            log.debug("No active background generation for user {} type {}, nothing to stop", userId, resourceType);
            return;
        }

        context.future().cancel(false);
        log.info("Generation stopped for user {} and resource type {}", userId, resourceType);
    }

    private Runnable createTask(Long userId, ResourceType resourceType, GenerationMode mode, ProfileType profileType, int intervalSeconds) {
        return () -> {
            try {
                if (virtualClock.isPaused()){
                    return;
                }
                Instant curVirtualTime = virtualClock.instant();
                TaskKey key = new TaskKey(userId, resourceType);

                activeTasks.computeIfPresent(key, (k, context) -> {
                    GenerationTask task = context.task();

                    if (task.mode() == GenerationMode.REALISTIC && task.profileType() == ProfileType.TRIP) {
                        return new TaskContext(processTripPlan(task, curVirtualTime), context.future());
                    }

                    long virtualElapsedMillis = java.time.Duration.between(task.lastSentTime(), curVirtualTime).toMillis();
                    long intervalMillis = task.intervalSeconds() * 1000L;
                    if (virtualElapsedMillis >= intervalMillis){
                        long intervalsPassed = virtualElapsedMillis / intervalMillis;
                        long eventsToProcess = Math.min(intervalsPassed, MAX_EVENTS_PER_TICK);
                        double curAmount = 0.0;

                        for (int i = 0; i < eventsToProcess; i++){
                            curAmount += calculateAmount(mode, task.profileType(), resourceType, curVirtualTime);
                        }

                        double newAccumulatedAmount = task.accumulatedAmount() + curAmount;

                        long timeSinceLastKafkaSendMillis = java.time.Duration.between(task.lastKafkaSendTime(), curVirtualTime).toMillis();

                        if (timeSinceLastKafkaSendMillis >= 60_000L){
                            ConsumptionEvent event = ConsumptionEvent.create(
                                task.userId(),
                                task.resourceType(),
                                newAccumulatedAmount,
                                curVirtualTime,
                                systemZoneId,
                                false
                            );

                            publisher.publish(event);
                            task = new GenerationTask(
                                task.userId(),
                                task.resourceType(),
                                task.mode(),
                                task.profileType(), 
                                task.intervalSeconds(),
                                0.0,
                                curVirtualTime,
                                curVirtualTime,
                                task.tripState()
                            );
                        }else{
                            task = new GenerationTask(
                                task.userId(),
                                task.resourceType(),
                                task.mode(),
                                task.profileType(), 
                                task.intervalSeconds(),
                                newAccumulatedAmount,
                                curVirtualTime,
                                task.lastKafkaSendTime(),
                                task.tripState()
                            );
                        }
                    }
                    return new TaskContext(task, context.future());
                });

            } catch (Exception e) {
                log.error("Error in tick generation for user {}:{}", userId, e.getMessage(), e);
            }
        };
    }

    private double calculateAmount(GenerationMode mode, ProfileType profileType, ResourceType resourceType, Instant curVirtualTime) {
        return switch (mode) {
            case SIMPLE -> calculateSimpleAmount(resourceType);
            case REALISTIC -> {
                if (profileType == ProfileType.STANDARD) {
                    yield calculateRealisticStandardAmount(resourceType, curVirtualTime);
                } else {
                    throw new IllegalStateException("Invalid profile for calculateAmount: " + profileType);
                }
            }
        };
    }

    private double calculateSimpleAmount(ResourceType resourceType) {
        return switch (resourceType) {
            case TRAFFIC -> properties.getTrafficBaseAmountInGB();
            case MINUTES -> properties.getMinutesBaseAmount();
        };
    }

    private double calculateRealisticStandardAmount(ResourceType resourceType, Instant virtualTime) {
        int hour = virtualTime.atZone(systemZoneId).getHour();
        
        return switch (resourceType) {
            case TRAFFIC -> calculateTrafficForHour(hour);
            case MINUTES -> calculateMinutesForHour(hour);
        };
    }

    private double calculateTrafficForHour(int hour){
        double amountInMB;
        
        if (hour >= 23 || hour < 7){
            amountInMB = randomBetween(0.5, 2.0);
        } else if (hour >= 7 && hour < 9){
            if (Math.random() < 0.8){
                amountInMB = randomBetween(2.0, 10.0);
            } else{
                amountInMB = randomBetween(10.0, 20.0);
            }
        } else if (hour >= 9 && hour < 18){
            amountInMB = randomBetween(0.5, 5.0);
        } else{
            amountInMB = randomBetween(15.0, 50.0);
        }
        return amountInMB / 1024.0;
    }

    private double calculateMinutesForHour(int hour){
        
        if (hour >= 23 || hour < 7){
            return randomBetween(0.0, 1.0);
        } else if (hour >= 7 && hour < 9){
            return randomBetween(1.0, 5.0);
        } else if (hour >= 9 && hour < 18){
            return randomBetween(0.0, 2.0);
        } else{
            return randomBetween(2.0, 10.0);
        }
    }

    private double randomBetween(double min, double max){
        return min + (max - min) * Math.random();
    }

    private GenerationTask processTripPlan(GenerationTask task, Instant now) {
        TripState state = task.tripState();
        if (state == null) {
            return task;
        }
        
        List<TripProfilePlanner.PlannedEvent> plan = state.plan();
        int index = state.index();
        int processed = 0;

        while (index < plan.size()
                && !plan.get(index).publishedAt().isAfter(now)
                && processed < MAX_EVENTS_PER_TICK) {

            TripProfilePlanner.PlannedEvent event = plan.get(index);

            publisher.publish(ConsumptionEvent.create(
                task.userId(),
                task.resourceType(),
                event.amountGB(),
                event.consumedAt(),
                systemZoneId,
                false
            ));

            index++;
            processed++;
        }

        if (index >= plan.size()) {
            TripPlan next = tripPlanner.plan(now, systemZoneId);
            return task.withTripState(new TripState(next.events(), 0));
        }

        return task.withTripState(new TripState(plan, index));
    }

    public void stopAllBackgroundGeneration() {
        activeTasks.forEach((key, context) -> {
            context.future().cancel(false);
            log.info("Stopped background generation for user {} type {}", key.userId(), key.resourceType());
        });
        activeTasks.clear();
        activeAnomalies.forEach((userId, ctx) -> {
            ctx.future().cancel(false);
            log.info("Stopped anomaly for user {}", userId);
        });
        activeAnomalies.clear();
    }

    public void startAnomaly(Long userId, ResourceType resourceType) {
        if (activeAnomalies.containsKey(userId)) {
            throw new GenerationAlreadyStartedException(userId, resourceType);
        }

        double percent = 15.0 + Math.random() * 35.0;
        double baseDaily = (resourceType == ResourceType.TRAFFIC) ? 0.4 : 60.0;
        double totalAmount = baseDaily * percent / 100.0;

        int totalEvents = 180;
        double amountPerEvent = totalAmount / totalEvents;

        Instant startTime = virtualClock.instant();
        long durationVirtualSeconds = 6L * 3600L;
        long intervalVirtualSeconds = 120L;

        AnomalyState state = new AnomalyState(
                userId, resourceType, amountPerEvent, startTime,
                durationVirtualSeconds, intervalVirtualSeconds, 0, startTime
        );

        Runnable task = () -> tickAnomaly(userId);
        ScheduledFuture<?> future = scheduledExecutorService.scheduleAtFixedRate(task, 0, 50, TimeUnit.MILLISECONDS);

        activeAnomalies.put(userId, new AnomalyContext(state, future));
        log.info("Anomaly started for user {} type {} totalAmount={} events={}",
                userId, resourceType, totalAmount, totalEvents);
    }

    public void stopAnomaly(Long userId) {
        AnomalyContext ctx = activeAnomalies.remove(userId);
        if (ctx != null) {
            ctx.future().cancel(false);
            log.info("Anomaly stopped for user {}", userId);
        }
    }

    private void tickAnomaly(Long userId) {
        try {
            AnomalyContext ctx = activeAnomalies.get(userId);
            if (ctx == null) return;

            AnomalyState state = ctx.state();
            Instant now = virtualClock.instant();
            long elapsed = now.toEpochMilli() - state.startTime().toEpochMilli();

            if (elapsed >= state.durationVirtualSeconds() * 1000L) {
                stopAnomaly(userId);
                return;
            }

            long expectedTicks = elapsed / (state.intervalVirtualSeconds() * 1000L);
            int sent = state.sentCount();

            while (sent < expectedTicks && sent < 180) {
                Instant eventTime = state.startTime().plusSeconds((long)(sent + 1) * state.intervalVirtualSeconds());
                publisher.publish(ConsumptionEvent.create(
                        state.userId(),
                        state.resourceType(),
                        state.amountPerEvent(),
                        eventTime,
                        systemZoneId,
                        true
                ));
                sent++;
            }

            if (sent != state.sentCount()) {
                final int finalSent = sent;
                activeAnomalies.computeIfPresent(userId, (k, current) ->
                        new AnomalyContext(state.withSentCount(finalSent), current.future())
                );
            }
        } catch (Exception e) {
            log.error("Error in anomaly tick for user {}", userId, e);
        }
    }

    public java.util.List<TaskStatus> getActiveTasksStatus() {
        java.util.List<TaskStatus> result = new java.util.ArrayList<>();
        
        activeTasks.forEach((key, context) -> {
            result.add(new TaskStatus(
                key.userId(),
                key.resourceType(),
                context.task().mode(),
                context.task().profileType(),
                context.task().intervalSeconds(),
                "BACKGROUND"
            ));
        });
        
        activeAnomalies.forEach((userId, ctx) -> {
            result.add(new TaskStatus(
                userId,
                ctx.state().resourceType(),
                null,
                null,
                0,
                "ANOMALY"
            ));
        });
        
        return result;
    }

    public record TaskStatus(
        Long userId,
        ResourceType resourceType,
        GenerationMode mode,
        ProfileType profileType,
        int intervalSeconds,
        String taskType
    ) {}

    private record TaskKey(
        Long userId, 
        ResourceType resourceType
    ) {}

    private record GenerationTask(
        Long userId,
        ResourceType resourceType,
        GenerationMode mode,
        ProfileType profileType,
        int intervalSeconds,
        double accumulatedAmount,
        Instant lastSentTime,
        Instant lastKafkaSendTime,
        TripState tripState
    ) {
            GenerationTask withTripState(TripState newState) {
            return new GenerationTask(
                userId, resourceType, mode, profileType, intervalSeconds,
                accumulatedAmount, lastSentTime, lastKafkaSendTime, newState
            );
        }
    }

    private record TaskContext(
        GenerationTask task,
        ScheduledFuture<?> future
    ) {}

    private record TripState(
        List<TripProfilePlanner.PlannedEvent> plan,
        int index
    ) {}

    private record AnomalyState(
        Long userId,
        ResourceType resourceType,
        double amountPerEvent,
        Instant startTime,
        long durationVirtualSeconds,
        long intervalVirtualSeconds,
        int sentCount,
        Instant lastTickTime
    ) {
        AnomalyState withSentCount(int newSentCount) {
            return new AnomalyState(
                userId, resourceType, amountPerEvent, startTime,
                durationVirtualSeconds, intervalVirtualSeconds, newSentCount, lastTickTime
            );
        }
    }

    private record AnomalyContext(
        AnomalyState state,
        ScheduledFuture<?> future
    ) {}
}
