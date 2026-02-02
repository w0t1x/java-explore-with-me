package ru.practicum.statsclient;

import ru.practicum.statsdto.EndpointHit;
import ru.practicum.statsdto.ViewStats;

import java.time.LocalDateTime;
import java.util.List;

public interface StatsClient {
    void hit(EndpointHit endpointHit);
    List<ViewStats> getStats(LocalDateTime start, LocalDateTime end,
                             List<String> uris, boolean unique);
}