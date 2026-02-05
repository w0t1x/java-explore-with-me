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
            log.warn("Запрос не может быть null");
            return;
        }

        try {
            String app = appName;
            String uri = request.getRequestURI();
            String ip = getClientIp(request);

            // ВАЖНО: проверяем, что URI не null
            if (uri == null || uri.isEmpty()) {
                log.warn("URI is null or empty");
                return;
            }

            EndpointHit hit = EndpointHit.builder()
                    .app(app)
                    .uri(uri)
                    .ip(ip)
                    .timestamp(LocalDateTime.now())
                    .build();

            // Проверить, что statsClient не null
            if (statsClient != null) {
                try {
                    statsClient.hit(hit);
                    log.debug("Сохранен просмотр: {}", hit);
                } catch (Exception e) {
                    log.warn("Не удалось отправить статистику: {}", e.getMessage());
                }
            } else {
                log.warn("StatsClient is null, cannot save hit");
            }
        } catch (Exception e) {
            log.warn("Не удалось сохранить просмотр в сервисе статистики: {}", e.getMessage());
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

        // Если IP содержит несколько адресов (при использовании прокси), берем первый
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }
}