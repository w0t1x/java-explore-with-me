package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.compilation.CompilationDto;
import ru.practicum.dto.compilation.NewCompilationDto;
import ru.practicum.dto.compilation.UpdateCompilationRequest;
import ru.practicum.dto.event.EventShortDto;
import ru.practicum.exception.NotFoundException;
import ru.practicum.mapper.CompilationMapper;
import ru.practicum.mapper.EventMapper;
import ru.practicum.model.Compilation;
import ru.practicum.model.Event;
import ru.practicum.model.RequestStatus;
import ru.practicum.repository.CompilationRepository;
import ru.practicum.repository.ConfirmedCount;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.ParticipationRequestRepository;

import java.util.*;
import java.util.stream.Collectors;

import static ru.practicum.util.PageableUtil.fromOffset;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompilationService {

    private static final long DEFAULT_COUNT = 0L;

    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;
    private final StatsService statsService;
    private final ParticipationRequestRepository requestRepository;

    @Transactional
    public CompilationDto create(NewCompilationDto dto) {
        Set<Event> events = new HashSet<>();
        if (dto.getEvents() != null && !dto.getEvents().isEmpty()) {
            events.addAll(eventRepository.findAllByIdIn(dto.getEvents()));
        }

        Compilation compilation = Compilation.builder()
                .title(dto.getTitle())
                .pinned(Boolean.TRUE.equals(dto.getPinned()))
                .events(events)
                .build();

        Compilation saved = compilationRepository.save(compilation);
        return toDtoWithEvents(saved);
    }

    @Transactional
    public void delete(long compId) {
        if (!compilationRepository.existsById(compId)) {
            throw new NotFoundException("Compilation with id=" + compId + " was not found");
        }
        compilationRepository.deleteById(compId);
    }

    @Transactional
    public CompilationDto update(long compId, UpdateCompilationRequest dto) {
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Compilation with id=" + compId + " was not found"));

        if (dto.getTitle() != null) {
            compilation.setTitle(dto.getTitle());
        }
        if (dto.getPinned() != null) {
            compilation.setPinned(dto.getPinned());
        }
        if (dto.getEvents() != null) {
            Set<Event> events = new HashSet<>();
            if (!dto.getEvents().isEmpty()) {
                events.addAll(eventRepository.findAllByIdIn(dto.getEvents()));
            }
            compilation.setEvents(events);
        }

        Compilation saved = compilationRepository.save(compilation);
        return toDtoWithEvents(saved);
    }

    public List<CompilationDto> getAll(Boolean pinned, int from, int size) {
        Pageable pageable = fromOffset(from, size, Sort.by("id").ascending());

        List<Compilation> comps = (pinned == null)
                ? compilationRepository.findAll(pageable).getContent()
                : compilationRepository.findAllByPinned(pinned, pageable).getContent();

        return comps.stream()
                .map(this::toDtoWithEvents)
                .collect(Collectors.toList());
    }

    public CompilationDto getById(long compId) {
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Compilation with id=" + compId + " was not found"));
        return toDtoWithEvents(compilation);
    }

    private CompilationDto toDtoWithEvents(Compilation compilation) {
        List<Event> events = new ArrayList<>(compilation.getEvents());

        Map<Long, Long> views = statsService.getViewsForEvents(events);
        Map<Long, Long> confirmed = getConfirmedCounts(events);

        List<EventShortDto> eventDtos = events.stream()
                .sorted(Comparator.comparingLong(Event::getId))
                .map(e -> EventMapper.toShortDto(
                        e,
                        confirmed.getOrDefault(e.getId(), DEFAULT_COUNT),
                        views.getOrDefault(e.getId(), DEFAULT_COUNT)
                ))
                .collect(Collectors.toList());

        return CompilationMapper.toDto(compilation, eventDtos);
    }

    private Map<Long, Long> getConfirmedCounts(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Map.of();
        }

        List<Long> eventIds = events.stream()
                .map(Event::getId)
                .toList();

        List<ConfirmedCount> counts =
                requestRepository.countByEventIdsAndStatus(eventIds, RequestStatus.CONFIRMED);

        Map<Long, Long> confirmed = new HashMap<>();
        for (ConfirmedCount cc : counts) {
            confirmed.put(cc.getEventId(), cc.getCnt());
        }
        return confirmed;
    }
}
