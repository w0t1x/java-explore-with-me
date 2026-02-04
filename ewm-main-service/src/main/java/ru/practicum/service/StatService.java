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
        String app = appName;
        String uri = request.getRequestURI();
        String ip = getClientIp(request);

        EndpointHit hit = EndpointHit.builder()
                .app(app)
                .uri(uri)
                .ip(ip)
                .timestamp(LocalDateTime.now())
                .build();

        try {
            statsClient.hit(hit);
            log.debug("Saved hit: {}", hit);
        } catch (Exception e) {
            log.error("Failed to save hit: {}", e.getMessage());
        }
    }

    private String getClientIp(HttpServletRequest request) {
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
        return ip;
    }
}
