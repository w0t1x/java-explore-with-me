package ru.practicum.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.practicum.statsclient.StatsClient;
import ru.practicum.statsdto.EndpointHit;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatService {

    private final StatsClient statsClient;

    @Value("${app.name:ewm-main-service}")
    private String appName;

    public void saveHit(HttpServletRequest request) {
        if (request == null) {
            log.warn("Request is null in saveHit");
            return;
        }

        try {
            String app = appName;
            String uri = request.getRequestURI();
            String ip = getClientIp(request);

            if (uri == null || uri.isEmpty()) {
                log.warn("URI is null or empty for request: {}", request);
                return;
            }

            EndpointHit hit = EndpointHit.builder()
                    .app(app)
                    .uri(uri)
                    .ip(ip != null ? ip : "0.0.0.0")
                    .timestamp(LocalDateTime.now())
                    .build();

            log.debug("Sending hit to stats service: {}", hit);

            if (statsClient != null) {
                statsClient.hit(hit);
                log.debug("Successfully saved hit for URI: {}", uri);
            } else {
                log.warn("StatsClient is null, cannot save hit for URI: {}", uri);
            }
        } catch (Exception e) {
            log.error("Error saving hit for request {}: {}", request.getRequestURI(), e.getMessage());
            // статистика не должна ломать основную логику
        }
    }

    // было private -> стало public (используется в fallback-подсчёте views)
    public String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }
}
