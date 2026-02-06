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

        // Устанавливаем значения по умолчанию, если они не пришли
        if (event.getPaid() == null) event.setPaid(false);
        if (event.getParticipantLimit() == null) event.setParticipantLimit(0);
        if (event.getRequestModeration() == null) event.setRequestModeration(true);

        Event savedEvent = eventRepository.save(event);
        log.info("Создано событие с id={} пользователем id={}", savedEvent.getId(), userId);

        return eventMapper.toEventFullDto(savedEvent);
    }

    public List<EventShortDto> getEventsByUser(Long userId, Integer from, Integer size) {
        validatePaginationParams(from, size);

        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));

        int page = from / size;
        Pageable pageable = PageRequest.of(page, size, Sort.by("eventDate").descending());
        Page<Event> eventPage = eventRepository.findByInitiatorId(userId, pageable);

        if (eventPage == null || eventPage.isEmpty()) {
            return Collections.emptyList();
        }

        List<Event> events = eventPage.getContent();
        Map<Long, Long> views = getViewsForEvents(events);
        events.forEach(event -> {
            event.setViews(views.getOrDefault(event.getId(), 0L));
        });

        return events.stream()
                .map(eventMapper::toEventShortDto)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public EventFullDto getEventByUser(Long userId, Long eventId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));

        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        List<ViewStats> stats = getStatsForEvent(event);
        event.setViews(stats.isEmpty() ? 0L : stats.get(0).getHits());

        EventFullDto result = eventMapper.toEventFullDto(event);
        if (result == null) {
            throw new RuntimeException("Ошибка при маппинге события");
        }
        return result;
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

        // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: проверка даты на уже наступившую -> 400, а не 409
        if (updateRequest.getEventDate() != null) {
            LocalDateTime now = LocalDateTime.now();
            if (updateRequest.getEventDate().isBefore(now)) {
                throw new ValidationException("Дата события не может быть в прошлом");
            }
            if (updateRequest.getEventDate().isBefore(now.plusHours(2))) {
                throw new ValidationException("До даты мероприятия должно быть не менее 2 часов");
            }
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
        EventFullDto result = eventMapper.toEventFullDto(updatedEvent);
        if (result == null) {
            throw new RuntimeException("Ошибка при маппинге обновленного события");
        }
        return result;
    }

    public List<EventFullDto> getEventsByAdmin(List<Long> users, List<EventState> states, List<Long> categories,
                                               LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                               Integer from, Integer size) {
        validatePaginationParams(from, size);

        // Фиксируем null для пустых списков
        List<Long> filteredUsers = (users == null || users.isEmpty()) ? null : users;
        List<EventState> filteredStates = (states == null || states.isEmpty()) ? null : states;
        List<Long> filteredCategories = (categories == null || categories.isEmpty()) ? null : categories;

        int page = from / size;
        Sort sort = Sort.by("id").descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Event> eventPage = eventRepository.findEventsByAdmin(
                filteredUsers, filteredStates, filteredCategories, rangeStart, rangeEnd, pageable);

        if (eventPage == null || eventPage.isEmpty()) {
            return Collections.emptyList();
        }

        List<Event> events = eventPage.getContent();
        Map<Long, Long> views = getViewsForEvents(events);
        events.forEach(event -> event.setViews(views.getOrDefault(event.getId(), 0L)));

        return events.stream()
                .map(eventMapper::toEventFullDto)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest updateRequest) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        if (updateRequest.getEventDate() != null) {
            if (updateRequest.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
                throw new ConflictException("До даты мероприятия должно быть не менее 1 часа");
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
                        throw new ConflictException("Невозможно отклонить событие, поскольку оно уже опубликовано");
                    }
                    event.setState(EventState.CANCELED);
                    break;
            }
        }

        updateEventFields(event, updateRequest);

        Event updatedEvent = eventRepository.save(event);
        EventFullDto result = eventMapper.toEventFullDto(updatedEvent);
        if (result == null) {
            throw new RuntimeException("Ошибка при маппинге события администратором");
        }
        return result;
    }

    public List<EventShortDto> getEventsPublic(String text, List<Long> categories, Boolean paid,
                                               LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                               Boolean onlyAvailable, String sort, Integer from, Integer size,
                                               HttpServletRequest request) {
        try {
            log.debug("Начало обработки публичного запроса событий с параметрами: text={}, categories={}, paid={}, " +
                            "rangeStart={}, rangeEnd={}, onlyAvailable={}, sort={}, from={}, size={}",
                    text, categories, paid, rangeStart, rangeEnd, onlyAvailable, sort, from, size);

            try {
                statService.saveHit(request);
            } catch (Exception e) {
                log.warn("Ошибка при сохранении статистики, но продолжаем обработку: {}", e.getMessage());
            }

            final Integer finalFrom;
            final Integer finalSize;

            if (from == null || from < 0) {
                finalFrom = 0;
            } else {
                finalFrom = from;
            }

            if (size == null || size <= 0) {
                finalSize = 10;
            } else {
                finalSize = size;
            }

            if (finalSize == 0) {
                throw new ValidationException("Параметр size должен быть больше 0");
            }

            final List<Long> finalCategories;
            if (categories != null && categories.isEmpty()) {
                finalCategories = null;
            } else {
                finalCategories = categories;
            }

            final String finalText;
            if (text != null && !text.trim().isEmpty()) {
                String trimmedText = text.trim();
                if (trimmedText.length() < 1 || trimmedText.length() > 7000) {
                    throw new ValidationException("Текст поиска должен быть от 1 до 7000 символов");
                }
                finalText = trimmedText;
            } else {
                finalText = null;
            }

            final LocalDateTime finalRangeStart;
            final LocalDateTime finalRangeEnd;

            if (rangeStart == null && rangeEnd == null) {
                finalRangeStart = LocalDateTime.now();
                finalRangeEnd = null;
            } else {
                finalRangeStart = rangeStart;
                finalRangeEnd = rangeEnd;
            }

            if (finalRangeStart != null && finalRangeEnd != null
                    && finalRangeStart.isAfter(finalRangeEnd)) {
                throw new ValidationException("Начальная дата не может быть позже конечной");
            }

            final Boolean finalOnlyAvailable = onlyAvailable != null ? onlyAvailable : false;

            final String finalSort;
            if (sort != null && !sort.isEmpty()) {
                if (!sort.equals("EVENT_DATE") && !sort.equals("VIEWS")) {
                    throw new ValidationException("Некорректный параметр сортировки. Допустимые значения: EVENT_DATE, VIEWS");
                }
                finalSort = sort;
            } else {
                finalSort = null;
            }

            // 5. Создание пагинации
            final int page = finalFrom / finalSize;
            final Pageable pageable;

            if (finalSort == null || finalSort.equals("EVENT_DATE")) {
                // Сортировка по дате события по возрастанию (ближайшие события первыми)
                pageable = PageRequest.of(page, finalSize, Sort.by("eventDate").ascending());
            } else {
                // Для сортировки по просмотрам сначала получаем без сортировки, затем сортируем в памяти
                pageable = PageRequest.of(page, finalSize);
            }

            log.debug("Параметры после нормализации: text='{}', categories={}, paid={}, rangeStart={}, " +
                            "rangeEnd={}, onlyAvailable={}, sort={}, page={}, pageSize={}",
                    finalText, finalCategories, paid, finalRangeStart, finalRangeEnd,
                    finalOnlyAvailable, finalSort, page, finalSize);

            final Page<Event> eventPage;
            try {
                eventPage = eventRepository.findEventsPublic(
                        finalText, finalCategories, paid, finalRangeStart,
                        finalRangeEnd, finalOnlyAvailable, pageable);

                if (eventPage == null) {
                    log.warn("Результат запроса событий вернул null, возвращаем пустой список");
                    return Collections.emptyList();
                }
            } catch (Exception e) {
                log.error("Ошибка при запросе событий из базы данных: {}", e.getMessage(), e);
                throw new ValidationException("Ошибка при выполнении запроса событий");
            }

            final List<Event> events = eventPage.getContent();

            if (events.isEmpty()) {
                log.debug("События не найдены по заданным критериям");
                return Collections.emptyList();
            }

            log.debug("Найдено {} событий", events.size());

            final Map<Long, Long> viewsMap;
            try {
                viewsMap = getViewsForEvents(events);
            } catch (Exception e) {
                log.warn("Не удалось получить статистику просмотров: {}", e.getMessage());
                return events.stream()
                        .map(event -> {
                            try {
                                // Без просмотров создаем DTO
                                return eventMapper.toEventShortDto(event);
                            } catch (Exception ex) {
                                log.error("Ошибка при маппинге события с id={}: {}", event.getId(), ex.getMessage());
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }

            events.forEach(event -> {
                Long views = viewsMap.get(event.getId());
                event.setViews(views != null ? views : 0L);
            });

            final List<Event> sortedEvents;
            if (finalSort != null && finalSort.equals("VIEWS")) {
                sortedEvents = new ArrayList<>(events);
                sortedEvents.sort((e1, e2) -> Long.compare(e2.getViews(), e1.getViews()));
            } else {
                sortedEvents = events;
            }

            final List<EventShortDto> result = sortedEvents.stream()
                    .map(event -> {
                        try {
                            return eventMapper.toEventShortDto(event);
                        } catch (Exception e) {
                            log.error("Ошибка при маппинге события с id={}: {}", event.getId(), e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            log.debug("Успешно обработан запрос событий, возвращено {} DTO", result.size());
            return result;

        } catch (ValidationException e) {
            log.warn("Ошибка валидации при запросе событий: {}", e.getMessage());
            throw e;
        } catch (NotFoundException e) {
            log.warn("Объект не найден при запросе событий: {}", e.getMessage());
            throw e;
        } catch (ConflictException e) {
            log.warn("Конфликт при запросе событий: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Непредвиденная ошибка при обработке публичного запроса событий: {}", e.getMessage(), e);
            throw new RuntimeException("Внутренняя ошибка сервера при обработке запроса событий", e);
        }
    }

    public EventFullDto getEventPublic(Long eventId, HttpServletRequest request) {
        statService.saveHit(request);

        try {
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

            if (event.getState() != EventState.PUBLISHED) {
                throw new NotFoundException("Событие с id=" + eventId + " не найдено");
            }

            // Получаем статистику просмотров и увеличиваем на 1
            List<ViewStats> stats = getStatsForEvent(event);
            Long currentViews = stats.isEmpty() ? 0L : stats.get(0).getHits();
            event.setViews(currentViews + 1); // Увеличиваем просмотры на 1

            EventFullDto result = eventMapper.toEventFullDto(event);
            if (result == null) {
                throw new RuntimeException("Ошибка при маппинге публичного события");
            }
            return result;

        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при получении публичного события {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Ошибка при обработке запроса события");
        }
    }

    private Pageable createPageableForPublicEvents(String sort, int page, int size) {
        if ("VIEWS".equals(sort)) {
            return PageRequest.of(page, size);
        } else {
            return PageRequest.of(page, size, Sort.by("eventDate").ascending());
        }
    }

    private void validatePaginationParams(Integer from, Integer size) {
        if (from == null || from < 0) {
            from = 0;
        }
        if (size == null || size <= 0) {
            size = 10;
        }
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
        if (updateRequest.getTitle() != null && !updateRequest.getTitle().isBlank()) {
            event.setTitle(updateRequest.getTitle());
        }
    }

    private void updateEventFields(Event event, UpdateEventAdminRequest updateRequest) {
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
        if (updateRequest.getTitle() != null && !updateRequest.getTitle().isBlank()) {
            event.setTitle(updateRequest.getTitle());
        }
    }

    private Map<Long, Long> getViewsForEvents(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            final List<String> uris = events.stream()
                    .map(event -> "/events/" + event.getId())
                    .collect(Collectors.toList());

            final LocalDateTime start = LocalDateTime.now().minusYears(1);
            final LocalDateTime end = LocalDateTime.now();

            final List<ViewStats> stats = statsClient.getStats(start, end, uris, true);

            if (stats == null) {
                return Collections.emptyMap();
            }

            final Map<Long, Long> viewsMap = new HashMap<>();
            for (ViewStats stat : stats) {
                try {
                    final String uri = stat.getUri();
                    if (uri != null && uri.startsWith("/events/")) {
                        String idStr = uri.substring("/events/".length());
                        if (idStr.contains("?")) {
                            idStr = idStr.substring(0, idStr.indexOf('?'));
                        }
                        final Long eventId = Long.parseLong(idStr);
                        viewsMap.put(eventId, stat.getHits());
                    }
                } catch (Exception e) {
                    log.warn("Не удалось обработать статистику для URI: {}", stat.getUri());
                }
            }

            return viewsMap;
        } catch (Exception e) {
            log.error("Ошибка при получении статистики просмотров: {}", e.getMessage());
            throw new RuntimeException("Не удалось получить статистику просмотров", e);
        }
    }

    private List<ViewStats> getStatsForEvent(Event event) {
        if (event == null) {
            return Collections.emptyList();
        }

        String uri = "/events/" + event.getId();
        LocalDateTime start = LocalDateTime.now().minusYears(1);
        LocalDateTime end = LocalDateTime.now();

        try {
            List<ViewStats> stats = statsClient.getStats(start, end, List.of(uri), true);
            return stats != null ? stats : Collections.emptyList();
        } catch (Exception e) {
            log.error("Ошибка при получении статистики по событию {}: {}", event.getId(), e.getMessage());
            return Collections.emptyList();
        }
    }
}