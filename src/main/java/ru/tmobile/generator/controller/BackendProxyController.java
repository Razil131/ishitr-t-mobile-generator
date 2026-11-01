package ru.tmobile.generator.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BackendProxyController {

    private final RestTemplate restTemplate;

    @Value("${backend.api.base-url:http://localhost:8080}")
    private String backendBaseUrl;

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getUsers() {
        try {
            String url = backendBaseUrl + "/api/v1/users";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {}
            );
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.warn("Failed to proxy GET /users from Backend: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(List.of());
        }
    }

    @GetMapping("/groups")
    public ResponseEntity<List<Map<String, Object>>> getGroups() {
        try {
            String url = backendBaseUrl + "/api/v1/groups";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {}
            );
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.warn("Failed to proxy GET /groups from Backend: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(List.of());
        }
    }

    @GetMapping("/tariffs")
    public ResponseEntity<List<Map<String, Object>>> getTariffs() {
        try {
            String url = backendBaseUrl + "/api/v1/tariffs";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {}
            );
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.warn("Failed to proxy GET /tariffs from Backend: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(List.of());
        }
    }

    @PostMapping("/tariffs")
    public ResponseEntity<Map<String, Object>> createTariff(@RequestBody Map<String, Object> body) {
        try {
            String url = backendBaseUrl + "/api/v1/tariffs";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<>() {}
            );
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (Exception e) {
            log.warn("Failed to proxy POST /tariffs to Backend: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/tariffs/{id}")
    public ResponseEntity<Map<String, String>> deleteTariff(@PathVariable Long id) {
        try {
            String url = backendBaseUrl + "/api/v1/tariffs/" + id;
            restTemplate.delete(url);
            return ResponseEntity.noContent().build();
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.warn("Failed to proxy DELETE /tariffs/{} to Backend: {}", id, e.getMessage());
            String message = "Ошибка при удалении тарифа";
            try {
                var body = e.getResponseBodyAs(Map.class);
                if (body != null && body.containsKey("message")) {
                    message = (String) body.get("message");
                }
            } catch (Exception ignored) {}
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", message));
        } catch (Exception e) {
            log.warn("Failed to proxy DELETE /tariffs/{} to Backend: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", e.getMessage()));
        }
    }
}