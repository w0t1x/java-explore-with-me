package ru.practicum.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.event.*;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.exception.ValidationException;
import ru.practicum.mapper.EventMapper;
import ru.practicum.model.Category;
import ru.practicum.model.Event;
import ru.practicum.model.User;
import ru.practicum.repository.CategoryRepository;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.UserRepository;
import ru.practicum.statsclient.StatsClient;
import ru.practicum.statsdto.ViewStats;
import ru.practicum.util.EventState;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final EventMapper eventMapper;
    private final StatsClient statsClient;
    private final StatService statService;

    /**
     * Fallback-хранилище уникальных просмотров по IP.
     * Используется как запасной вариант (например, если stats-сервис временно недоступен/не успел обработать hit).
     */
    private final Map<Long, Set<String>> fallbackUniqueViews = new ConcurrentHashMap<>();

    private long registerFallbackView(long eventId, HttpServletRequest request) {
        String ip = (request == null) ? null : statService.getClientIp(request);
        if (ip == null || ip.isBlank()) {
            ip = "0.0.0.0";
        }

        Set<String> ips = fallbackUniqueViews.computeIfAbsent(eventId, id -> ConcurrentHashMap.newKeySet());
        ips.add(ip);
        return ips.size();
    }

    @Transactional
    public EventFullDto createEvent(Long userId, NewEventDto newEventDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));

        Category category = categoryRepository.findById(newEventDto.getCategory())
                .orElseThrow(() -> new NotFoundException("Категория с id=" + newEventDto.getCategory() + " не найдена"));

        LocalDateTime eventDate = newEventDto.getEventDate();
        if (eventDate == null || eventDate.isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("До даты мероприятия должно быть не менее 2 часов");
        }

        Event event = eventMapper.toEvent(newEventDto);
        event.setCategory(category);
        event.setInitiator(user);
        event.setState(EventState.PENDING);
        event.setCreatedOn(LocalDateTime.now());
        event.setConfirmedRequests(0);
        event.setViews(0L);

        Event saved = eventRepository.save(event);
        return eventMapper.toEventFullDto(saved);
    }

    public List<EventShortDto> getEventsByUser(Long userId, Integer from, Integer size) {
        Pageable pageable = PageRequest.of(from / size, size);
        Page<Event> events = eventRepository.findByInitiatorId(userId, pageable);
        return events.getContent().stream()
                .map(eventMapper::toEventShortDto)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public EventFullDto getEventByUser(Long userId, Long eventId) {
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));
        return eventMapper.toEventFullDto(event);
    }

    @Transactional
    public EventFullDto updateEventByUser(Long userId, Long eventId, UpdateEventUserRequest updateRequest) {
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Нельзя изменить опубликованное событие");
        }

        if (updateRequest.getEventDate() != null &&
                updateRequest.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("До даты мероприятия должно быть не менее 2 часов");
        }

        updateEventFields(event, updateRequest);

        if (updateRequest.getStateAction() != null) {
            switch (updateRequest.getStateAction()) {
                case SEND_TO_REVIEW:
                    event.setState(EventState.PENDING);
                    break;
                case CANCEL_REVIEW:
                    event.setState(EventState.CANCELED);
                    break;
                default:
                    break;
            }
        }

        Event saved = eventRepository.save(event);
        return eventMapper.toEventFullDto(saved);
    }

    private void updateEventFields(Event event, UpdateEventUserRequest updateRequest) {
        if (updateRequest.getAnnotation() != null && !updateRequest.getAnnotation().isBlank()) {
            event.setAnnotation(updateRequest.getAnnotation());
        }
        if (updateRequest.getCategory() != null) {
            Category category = categoryRepository.findById(updateRequest.getCategory())
                    .orElseThrow(() -> new NotFoundException("Категория не найдена"));
            event.setCategory(category);
        }
        if (updateRequest.getDescription() != null && !updateRequest.getDescription().isBlank()) {
            event.setDescription(updateRequest.getDescription());
        }
        if (updateRequest.getEventDate() != null) {
            event.setEventDate(updateRequest.getEventDate());
        }
        if (updateRequest.getLocation() != null) {
            event.getLocation().setLat(updateRequest.getLocation().getLat());
            event.getLocation().setLon(updateRequest.getLocation().getLon());
        }
        if (updateRequest.getPaid() != null) {
            event.setPaid(updateRequest.getPaid());
        }
        if (updateRequest.getParticipantLimit() != null) {
            event.setParticipantLimit(updateRequest.getParticipantLimit());
        }
        if (updateRequest.getRequestModeration() != null) {
            event.setRequestModeration(updateRequest.getRequestModeration());
        }
        if (updateRequest.getTitle() != null && !updateRequest.getTitle().isBlank()) {
            event.setTitle(updateRequest.getTitle());
        }
    }

    private Pageable createPageableForPublicEvents(String sort, int page, int size) {
        if ("VIEWS".equals(sort)) {
            return PageRequest.of(page, size);
        } else {
            return PageRequest.of(page, size, Sort.by("eventDate").ascending());
        }
    }

    private Map<Long, Long> getViewsForEvents(List<Event> events) {
        Map<Long, Long> result = new HashMap<>();

        if (events == null || events.isEmpty()) {
            return result;
        }

        // База: то, что есть в сущности + fallback по IP
        for (Event e : events) {
            long dbViews = e.getViews() != null ? e.getViews() : 0L;
            long fallback = fallbackUniqueViews.getOrDefault(e.getId(), Collections.emptySet()).size();
            result.put(e.getId(), Math.max(dbViews, fallback));
        }

        // Основной источник — stats-сервис (уникальные IP)
        try {
            List<String> uris = events.stream()
                    .map(e -> "/events/" + e.getId())
                    .collect(Collectors.toList());

            LocalDateTime start = LocalDateTime.now().minusYears(1);
            LocalDateTime end = LocalDateTime.now().plusMinutes(1);

            List<ViewStats> stats = statsClient.getStats(start, end, uris, true);
            if (stats == null || stats.isEmpty()) {
                return result;
            }

            for (ViewStats stat : stats) {
                if (stat == null || stat.getUri() == null || stat.getHits() == null) {
                    continue;
                }

                String uri = stat.getUri();
                if (!uri.startsWith("/events/")) {
                    continue;
                }

                String idStr = uri.substring("/events/".length());
                int qIdx = idStr.indexOf('?');
                if (qIdx >= 0) {
                    idStr = idStr.substring(0, qIdx);
                }

                try {
                    Long eventId = Long.parseLong(idStr);
                    result.put(eventId, Math.max(result.getOrDefault(eventId, 0L), stat.getHits()));
                } catch (NumberFormatException ignore) {
                    // ignore
                }
            }

            return result;
        } catch (Exception e) {
            log.warn("Ошибка при получении статистики просмотров: {}", e.getMessage());
            return result;
        }
    }

    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest updateRequest) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        // Валидация даты:
        // - если дата в прошлом -> 400 (ValidationException)
        // - если дата слишком близко (меньше 1 часа) -> 409 (ConflictException)
        if (updateRequest.getEventDate() != null) {
            LocalDateTime now = LocalDateTime.now();
            if (updateRequest.getEventDate().isBefore(now)) {
                throw new ValidationException("Дата события не может быть в прошлом");
            }
            if (updateRequest.getEventDate().isBefore(now.plusHours(1))) {
                throw new ConflictException("Дата события должна быть не менее чем через 1 час от текущего момента");
            }
        }

        if (updateRequest.getStateAction() != null) {
            switch (updateRequest.getStateAction()) {
                case PUBLISH_EVENT:
                    if (event.getState() != EventState.PENDING) {
                        throw new ConflictException("Не удается опубликовать событие, потому что оно находится в неправильном состоянии: " + event.getState());
                    }
                    event.setState(EventState.PUBLISHED);
                    event.setPublishedOn(LocalDateTime.now());
                    break;
                case REJECT_EVENT:
                    if (event.getState() == EventState.PUBLISHED) {
                        throw new ConflictException("Не удается отклонить опубликованное событие");
                    }
                    event.setState(EventState.CANCELED);
                    break;
                default:
                    break;
            }
        }

        if (updateRequest.getAnnotation() != null) event.setAnnotation(updateRequest.getAnnotation());
        if (updateRequest.getCategory() != null) {
            Category category = categoryRepository.findById(updateRequest.getCategory())
                    .orElseThrow(() -> new NotFoundException("Категория не найдена"));
            event.setCategory(category);
        }
        if (updateRequest.getDescription() != null) event.setDescription(updateRequest.getDescription());
        if (updateRequest.getEventDate() != null) event.setEventDate(updateRequest.getEventDate());
        if (updateRequest.getLocation() != null) {
            event.getLocation().setLat(updateRequest.getLocation().getLat());
            event.getLocation().setLon(updateRequest.getLocation().getLon());
        }
        if (updateRequest.getPaid() != null) event.setPaid(updateRequest.getPaid());
        if (updateRequest.getParticipantLimit() != null) event.setParticipantLimit(updateRequest.getParticipantLimit());
        if (updateRequest.getRequestModeration() != null) event.setRequestModeration(updateRequest.getRequestModeration());
        if (updateRequest.getTitle() != null) event.setTitle(updateRequest.getTitle());

        Event saved = eventRepository.save(event);
        return eventMapper.toEventFullDto(saved);
    }

    public List<EventShortDto> getEventsPublic(String text, List<Long> categories, Boolean paid,
                                               LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                               Boolean onlyAvailable, String sort, Integer from, Integer size,
                                               HttpServletRequest request) {

        // записываем статистику, но не ломаем выдачу если stats недоступен
        try {
            statService.saveHit(request);
        } catch (Exception e) {
            log.warn("Не удалось сохранить статистику: {}", e.getMessage());
        }

        // Если даты не указаны - показываем будущие события
        LocalDateTime actualRangeStart = rangeStart;
        if (actualRangeStart == null && rangeEnd == null) {
            actualRangeStart = LocalDateTime.now();
        }

        int page = from / size;
        Pageable pageable = createPageableForPublicEvents(sort, page, size);

        Page<Event> eventPage = eventRepository.findEventsPublic(
                (text == null || text.isBlank()) ? null : text.trim(),
                (categories == null || categories.isEmpty()) ? null : categories,
                paid,
                actualRangeStart,
                rangeEnd,
                onlyAvailable != null && onlyAvailable,
                pageable
        );

        if (eventPage == null || eventPage.isEmpty()) {
            return Collections.emptyList();
        }

        List<Event> events = eventPage.getContent();
        Map<Long, Long> viewsMap = getViewsForEvents(events);

        List<EventShortDto> result = new ArrayList<>();
        for (Event event : events) {
            EventShortDto dto = eventMapper.toEventShortDto(event);
            if (dto != null) {
                dto.setViews(viewsMap.getOrDefault(event.getId(), 0L));
                result.add(dto);
            }
        }

        if ("VIEWS".equals(sort)) {
            result.sort((a, b) -> Long.compare(b.getViews(), a.getViews()));
        }

        return result;
    }

    public EventFullDto getEventPublic(Long eventId, HttpServletRequest request) {
        // Сохраняем обращение в stats (ошибка stats не должна ломать основную логику)
        statService.saveHit(request);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }

        long fallbackViews = registerFallbackView(eventId, request);

        long views = fallbackViews;
        List<ViewStats> stats = getStatsForEvent(event);
        if (stats != null && !stats.isEmpty() && stats.get(0) != null && stats.get(0).getHits() != null) {
            views = Math.max(stats.get(0).getHits(), fallbackViews);
        }

        EventFullDto result = eventMapper.toEventFullDto(event);
        result.setViews(views);
        return result;
    }

    private List<ViewStats> getStatsForEvent(Event event) {
        if (event == null) {
            return Collections.emptyList();
        }

        String uri = "/events/" + event.getId();
        LocalDateTime start = LocalDateTime.now().minusYears(1);
        LocalDateTime end = LocalDateTime.now().plusMinutes(1);

        try {
            List<ViewStats> stats = statsClient.getStats(start, end, List.of(uri), true);
            return stats != null ? stats : Collections.emptyList();
        } catch (Exception e) {
            log.error("Ошибка при получении статистики по событию {}: {}", event.getId(), e.getMessage());
            return Collections.emptyList();
        }
    }
}
