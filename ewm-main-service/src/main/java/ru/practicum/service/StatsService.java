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

    private static final long DEFAULT_HITS = 0L;
    private static final String EVENT_URI_PREFIX = "/events/";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private final StatsClient statsClient;

    @Value("${ewm.app:ewm-main-service}")
    private String appName;

    public void saveHit(HttpServletRequest request) {
        EndpointHit hit = new EndpointHit(
                appName,
                request.getRequestURI(),
                extractClientIp(request),
                LocalDateTime.now()
        );
        statsClient.hit(hit);
    }

    public Map<Long, Long> getViewsForEvents(Collection<Event> events) {
        if (events == null || events.isEmpty()) {
            return Map.of();
        }

        List<String> uris = events.stream()
                .map(e -> EVENT_URI_PREFIX + e.getId())
                .distinct()
                .collect(Collectors.toList());

        List<ViewStats> stats = statsClient.getStats(EPOCH, LocalDateTime.now(), uris, true);

        Map<String, Long> uriHits = new HashMap<>();
        for (ViewStats vs : stats) {
            uriHits.put(vs.getUri(), vs.getHits());
        }

        Map<Long, Long> result = new HashMap<>();
        for (Event e : events) {
            String uri = EVENT_URI_PREFIX + e.getId();
            result.put(e.getId(), uriHits.getOrDefault(uri, DEFAULT_HITS));
        }

        return result;
    }

    public long getViewsForEvent(long eventId) {
        List<ViewStats> stats = statsClient.getStats(
                EPOCH,
                LocalDateTime.now(),
                List.of(EVENT_URI_PREFIX + eventId),
                true
        );

        if (stats.isEmpty()) {
            return DEFAULT_HITS;
        }
        return stats.get(0).getHits();
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader(X_FORWARDED_FOR);
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
