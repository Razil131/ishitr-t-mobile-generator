package ru.tmobile.generator.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BackendUsersClient {

    private final RestTemplate restTemplate;

    @Value("${backend.api.base-url:http://localhost:8080}")
    private String backendBaseUrl;

    public List<Long> getActiveUserIds() {
        try {
            String url = backendBaseUrl + "/api/v1/users/active";
            Long[] users = restTemplate.getForObject(url, Long[].class);
            if (users == null) {
                log.warn("Backend returned null user list");
                return List.of();
            }
            return List.of(users);
        } catch (Exception e) {
            log.error("Failed to fetch active users from Backend: {}", e.getMessage());
            return List.of();
        }
    }
}