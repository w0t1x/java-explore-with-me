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
import ru.practicum.util.EventState;
import ru.practicum.statsclient.StatsClient;
import ru.practicum.statsdto.ViewStats;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
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

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Private API методы

    @Transactional
    public EventFullDto createEvent(Long userId, NewEventDto newEventDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));

        Category category = categoryRepository.findById(newEventDto.getCategory())
                .orElseThrow(() -> new NotFoundException("категория с id=" + newEventDto.getCategory() + " не найдена"));

        if (newEventDto.getParticipantLimit() < 0) {
            throw new ValidationException("Лимит участников не может быть отрицательным");
        }

        if (newEventDto.getParticipantLimit() < 0) {
            throw new ValidationException("Лимит участников не может быть отрицательным");
        }

        // Проверка на 2 часа до события
        if (newEventDto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ConflictException("До даты мероприятия должно быть не менее 2 часов");
        }

        Event event = eventMapper.toEvent(newEventDto);
        event.setCategory(category);
        event.setInitiator(user);
        event.setState(EventState.PENDING);
        event.setCreatedOn(LocalDateTime.now());
        event.setConfirmedRequests(0);
        event.setViews(0L);

        Event savedEvent = eventRepository.save(event);
        log.info("Созданное событие с id={} пользователем id={}", savedEvent.getId(), userId);

        return eventMapper.toEventFullDto(savedEvent);
    }

    public List<EventShortDto> getEventsByUser(Long userId, Integer from, Integer size) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));

        Pageable pageable = PageRequest.of(from / size, size, Sort.by("id").descending());
        List<Event> events = eventRepository.findByInitiatorId(userId, pageable).getContent();

        Map<Long, Long> views = getViewsForEvents(events);
        events.forEach(event -> event.setViews(views.getOrDefault(event.getId(), 0L)));

        return events.stream()
                .map(eventMapper::toEventShortDto)
                .collect(Collectors.toList());
    }

    public EventFullDto getEventByUser(Long userId, Long eventId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));

        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Пользователь id=" + eventId + " не найден"));

        List<ViewStats> stats = getStatsForEvent(event);
        event.setViews(stats.isEmpty() ? 0L : stats.get(0).getHits());

        return eventMapper.toEventFullDto(event);
    }

    @Transactional
    public EventFullDto updateEventByUser(Long userId, Long eventId, UpdateEventUserRequest updateRequest) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));

        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Можно изменить только отложенные или отмененные события");
        }

        // Проверка на отрицательный participantLimit
        if (updateRequest.getParticipantLimit() != null && updateRequest.getParticipantLimit() < 0) {
            throw new ValidationException("Лимит участников не может быть отрицательным");
        }

        // Проверка на дату события (минимум 2 часа от текущего момента)
        if (updateRequest.getEventDate() != null) {
            if (updateRequest.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
                throw new ConflictException("До даты мероприятия должно быть не менее 2 часов");
            }
        }

        // Проверка на дату события в прошлом (если передана)
        if (updateRequest.getEventDate() != null && updateRequest.getEventDate().isBefore(LocalDateTime.now())) {
            throw new ConflictException("Дата события не может быть в прошлом");
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
            }
        }

        Event updatedEvent = eventRepository.save(event);
        return eventMapper.toEventFullDto(updatedEvent);
    }

    // Admin API методы

    public List<EventFullDto> getEventsByAdmin(List<Long> users, List<EventState> states, List<Long> categories,
                                               LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                               Integer from, Integer size) {
        Pageable pageable = PageRequest.of(from / size, size, Sort.by("id").descending());

        Page<Event> eventPage = eventRepository.findEventsByAdmin(
                users, states, categories, rangeStart, rangeEnd, pageable);

        List<Event> events = eventPage.getContent();
        Map<Long, Long> views = getViewsForEvents(events);
        events.forEach(event -> event.setViews(views.getOrDefault(event.getId(), 0L)));

        return events.stream()
                .map(eventMapper::toEventFullDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest updateRequest) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        // Проверка на отрицательный participantLimit
        if (updateRequest.getParticipantLimit() != null && updateRequest.getParticipantLimit() < 0) {
            throw new ValidationException("Лимит участников не может быть отрицательным");
        }

        // Проверка на дату события (минимум 1 час от текущего момента для публикации)
        if (updateRequest.getEventDate() != null) {
            if (updateRequest.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
                throw new ConflictException("До даты мероприятия должно быть не менее 1 часа");
            }
        }

        // Проверка на дату события в прошлом (если передана)
        if (updateRequest.getEventDate() != null && updateRequest.getEventDate().isBefore(LocalDateTime.now())) {
            throw new ConflictException("Дата события не может быть в прошлом");
        }

        if (updateRequest.getStateAction() != null) {
            switch (updateRequest.getStateAction()) {
                case PUBLISH_EVENT:
                    if (event.getState() != EventState.PENDING) {
                        throw new ConflictException("Не удается опубликовать событие, потому что оно находится в неправильном состоянии: " + event.getState());
                    }
                    // Проверка, что дата события не менее чем через час от текущего момента
                    if (event.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
                        throw new ConflictException("Нельзя опубликовать событие, которое начинается менее чем через час");
                    }
                    event.setState(EventState.PUBLISHED);
                    event.setPublishedOn(LocalDateTime.now());
                    break;
                case REJECT_EVENT:
                    if (event.getState() == EventState.PUBLISHED) {
                        throw new ConflictException("Невозможно отклонить событие, поскольку оно уже опубликовано");
                    }
                    event.setState(EventState.CANCELED);
                    break;
            }
        }

        updateEventFields(event, updateRequest);

        Event updatedEvent = eventRepository.save(event);
        return eventMapper.toEventFullDto(updatedEvent);
    }

    // Public API методы

    public List<EventShortDto> getEventsPublic(String text, List<Long> categories, Boolean paid,
                                               LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                               Boolean onlyAvailable, String sort, Integer from, Integer size,
                                               HttpServletRequest request) {
        // Исправление деления при пагинации
        int page = from / size;
        Pageable pageable;
        if ("VIEWS".equals(sort)) {
            pageable = PageRequest.of(page, size, Sort.by("views").descending());
        } else {
            pageable = PageRequest.of(page, size, Sort.by("eventDate").descending());
        }

        if (rangeStart == null && rangeEnd == null) {
            rangeStart = LocalDateTime.now();
        }

        Page<Event> eventPage = eventRepository.findEventsPublic(
                text, categories, paid, rangeStart, rangeEnd, onlyAvailable, pageable);

        List<Event> events = eventPage.getContent();

        // Сохраняем статистику для публичного запроса
        if (request != null) {
            statService.saveHit(request);
        }

        // Получаем просмотры из сервиса статистики
        Map<Long, Long> views = getViewsForEvents(events);
        events.forEach(event -> event.setViews(views.getOrDefault(event.getId(), 0L)));

        return events.stream()
                .map(eventMapper::toEventShortDto)
                .collect(Collectors.toList());
    }

    public EventFullDto getEventPublic(Long eventId, HttpServletRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }

        // Сохраняем статистику
        statService.saveHit(request);

        // Получаем просмотры
        List<ViewStats> stats = getStatsForEvent(event);
        event.setViews(stats.isEmpty() ? 0L : stats.get(0).getHits());

        return eventMapper.toEventFullDto(event);
    }

    // Вспомогательные методы

    private void updateEventFields(Event event, UpdateEventUserRequest updateRequest) {
        if (updateRequest.getAnnotation() != null) {
            event.setAnnotation(updateRequest.getAnnotation());
        }
        if (updateRequest.getCategory() != null) {
            Category category = categoryRepository.findById(updateRequest.getCategory())
                    .orElseThrow(() -> new NotFoundException("Категория не найдена"));
            event.setCategory(category);
        }
        if (updateRequest.getDescription() != null) {
            event.setDescription(updateRequest.getDescription());
        }
        if (updateRequest.getEventDate() != null) {
            event.setEventDate(updateRequest.getEventDate());
        }
        if (updateRequest.getLocation() != null) {
            event.setLocation(ru.practicum.model.Location.builder()
                    .lat(updateRequest.getLocation().getLat())
                    .lon(updateRequest.getLocation().getLon())
                    .build());
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
        if (updateRequest.getTitle() != null) {
            event.setTitle(updateRequest.getTitle());
        }
    }

    private void updateEventFields(Event event, UpdateEventAdminRequest updateRequest) {
        if (updateRequest.getAnnotation() != null) {
            event.setAnnotation(updateRequest.getAnnotation());
        }
        if (updateRequest.getCategory() != null) {
            Category category = categoryRepository.findById(updateRequest.getCategory())
                    .orElseThrow(() -> new NotFoundException("Категория не найдена"));
            event.setCategory(category);
        }
        if (updateRequest.getDescription() != null) {
            event.setDescription(updateRequest.getDescription());
        }
        if (updateRequest.getEventDate() != null) {
            event.setEventDate(updateRequest.getEventDate());
        }
        if (updateRequest.getLocation() != null) {
            event.setLocation(ru.practicum.model.Location.builder()
                    .lat(updateRequest.getLocation().getLat())
                    .lon(updateRequest.getLocation().getLon())
                    .build());
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
        if (updateRequest.getTitle() != null) {
            event.setTitle(updateRequest.getTitle());
        }
    }

    private Map<Long, Long> getViewsForEvents(List<Event> events) {
        if (events.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> uris = events.stream()
                .map(event -> "/events/" + event.getId())
                .collect(Collectors.toList());

        LocalDateTime start = LocalDateTime.now().minusYears(1);
        LocalDateTime end = LocalDateTime.now().plusSeconds(1); // +1 секунда для включения текущего времени

        try {
            List<ViewStats> stats = statsClient.getStats(start, end, uris, true);

            return stats.stream()
                    .collect(Collectors.toMap(
                            stat -> {
                                String uri = stat.getUri();
                                return Long.parseLong(uri.substring(uri.lastIndexOf('/') + 1));
                            },
                            ViewStats::getHits,
                            (v1, v2) -> v1 + v2 // суммируем дубликаты
                    ));
        } catch (Exception e) {
            log.error("Ошибка при получении статистики из сервиса статистики: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private List<ViewStats> getStatsForEvent(Event event) {
        String uri = "/events/" + event.getId();
        LocalDateTime start = LocalDateTime.now().minusYears(1);
        LocalDateTime end = LocalDateTime.now();

        try {
            return statsClient.getStats(start, end, List.of(uri), true);
        } catch (Exception e) {
            log.error("Ошибка при получении статистики по событию {}: {}", event.getId(), e.getMessage());
            return Collections.emptyList();
        }
    }
}
