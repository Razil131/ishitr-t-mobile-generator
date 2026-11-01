package ru.tmobile.generator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "generator")
public class GeneratorBackgroundProperties {

    private double trafficBaseAmountInGB = 0.1;
    private double minutesBaseAmount = 20;

    private double tripDailyTrafficMinMB = 20.0;
    private double tripDailyTrafficMaxMB = 50.0;

    private double tripEventMinMB = 1.0;
    private double tripEventMaxMB = 5.0;

    private int tripMinEventsInBurst = 1;
    private int tripMaxEventsInBurst = 4;

    private int tripDelayMinMinutes = 2;
    private int tripDelayMaxMinutes = 5;
}