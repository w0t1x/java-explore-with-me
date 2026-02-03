package ru.practicum.statsserver;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.statsdto.EndpointHit;
import ru.practicum.statsdto.ViewStats;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final EndpointHitRepository repository;

    public void saveHit(EndpointHit hit) {
        EndpointHitEntity entity = EndpointHitEntity.builder()
                .app(hit.getApp())
                .uri(hit.getUri())
                .ip(hit.getIp())
                .timestamp(hit.getTimestamp())
                .build();
        repository.save(entity);
    }

    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end,
                                    List<String> uris, boolean unique) {
        if (unique) {
            return repository.getUniqueStats(start, end, uris);
        } else {
            return repository.getStats(start, end, uris);
        }
    }
}
