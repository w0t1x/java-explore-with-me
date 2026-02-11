package ru.practicum.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.practicum.model.Event;
import ru.practicum.statsclient.StatsClient;
import ru.practicum.statsdto.EndpointHit;
import ru.practicum.statsdto.ViewStats;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static ru.practicum.util.DateTimeUtil.EPOCH;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final StatsClient statsClient;

    @Value("${ewm.app:ewm-main-service}")
    private String appName;

    public void saveHit(HttpServletRequest request) {
        String ip = extractClientIp(request);

        EndpointHit hit = new EndpointHit(
                appName,
                request.getRequestURI(),
                ip,
                LocalDateTime.now()
        );
        statsClient.hit(hit);
    }

    public Map<Long, Long> getViewsForEvents(Collection<Event> events) {
        if (events == null || events.isEmpty()) {
            return Map.of();
        }

        List<String> uris = events.stream()
                .map(e -> "/events/" + e.getId())
                .distinct()
                .collect(Collectors.toList());

        List<ViewStats> stats = statsClient.getStats(EPOCH, LocalDateTime.now(), uris, true);

        Map<String, Long> uriHits = new HashMap<>();
        for (ViewStats vs : stats) {
            uriHits.put(vs.getUri(), vs.getHits());
        }

        Map<Long, Long> result = new HashMap<>();
        for (Event e : events) {
            String uri = "/events/" + e.getId();
            result.put(e.getId(), uriHits.getOrDefault(uri, 0L));
        }
        return result;
    }

    public long getViewsForEvent(long eventId) {
        List<ViewStats> stats = statsClient.getStats(EPOCH, LocalDateTime.now(),
                List.of("/events/" + eventId), true);
        if (stats.isEmpty()) return 0L;
        return stats.getFirst().getHits();
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
