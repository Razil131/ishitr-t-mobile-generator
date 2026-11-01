package ru.tmobile.generator.profile;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import ru.tmobile.generator.config.GeneratorBackgroundProperties;

@Component
@RequiredArgsConstructor
public class TripProfilePlanner {

    private final GeneratorBackgroundProperties properties;

    public record PlannedEvent(
        Instant consumedAt,
        Instant publishedAt,
        double amountGB
    ) {}

    public record TripPlan(
        List<PlannedEvent> events,
        Instant end
    ) {}

    public TripPlan plan(Instant from, ZoneId zone) {
        return plan(from, zone, ThreadLocalRandom.current());
    }

    public TripPlan plan(Instant from, ZoneId zone, Random random) {
        double targetMB = random.nextDouble(
            properties.getTripDailyTrafficMinMB(),
            properties.getTripDailyTrafficMaxMB()
        );

        List<List<Double>> bursts = new ArrayList<>();
        List<Integer> spacings = new ArrayList<>();
        double remaining = targetMB;

        while (remaining > 1e-9) {
            int burstSize = random.nextInt(
                properties.getTripMinEventsInBurst(),
                properties.getTripMaxEventsInBurst() + 1
            );
            List<Double> burst = new ArrayList<>();

            for (int i = 0; i < burstSize && remaining > 1e-9; i++) {
                double mb = Math.min(
                    random.nextDouble(
                        properties.getTripEventMinMB(),
                        properties.getTripEventMaxMB()
                    ),
                    remaining
                );
                burst.add(mb);
                remaining -= mb;
            }
            bursts.add(burst);
            spacings.add(random.nextInt(30, 91));
        }

        long burstSeconds = 0;
        for (int i = 0; i < bursts.size(); i++) {
            burstSeconds += (long) (bursts.get(i).size() - 1) * spacings.get(i);
        }

        long daySeconds = 86_400L;
        long leading = random.nextInt(0, 1801);
        double available = Math.max(1.0, daySeconds - leading - burstSeconds);

        int gapsCount = Math.max(1, bursts.size() - 1);
        double[] weights = new double[gapsCount];
        double weightSum = 0.0;
        for (int i = 0; i < gapsCount; i++) {
            weights[i] = random.nextDouble(0.2, 3.0);
            weightSum += weights[i];
        }

        List<PlannedEvent> events = new ArrayList<>();
        Instant cursor = from.plusSeconds(leading);
        int every = random.nextInt(3, 5); // 3 или 4
        int counter = 0;

        for (int b = 0; b < bursts.size(); b++) {
            if (b > 0) {
                long gap = Math.round(available * weights[b - 1] / weightSum);
                cursor = cursor.plusSeconds(gap);
            }

            List<Double> burst = bursts.get(b);
            int spacing = spacings.get(b);

            for (int j = 0; j < burst.size(); j++) {
                if (j > 0) {
                    cursor = cursor.plusSeconds(spacing);
                }

                Instant consumedAt = cursor;
                counter++;
                boolean delayed = counter % every == 0;

                Instant publishedAt = delayed
                    ? consumedAt.plusSeconds(
                        random.nextInt(
                            properties.getTripDelayMinMinutes(),
                            properties.getTripDelayMaxMinutes() + 1
                        ) * 60L
                    )
                    : consumedAt;

                events.add(new PlannedEvent(
                    consumedAt,
                    publishedAt,
                    burst.get(j) / 1024.0
                ));
            }
        }

        return new TripPlan(List.copyOf(events), from.plusSeconds(daySeconds));
    }
}