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

        log.info("Публичный запрос событий: text={}, categories={}, paid={}, rangeStart={}, " +
                        "rangeEnd={}, onlyAvailable={}, sort={}, from={}, size={}",
                text, categories, paid, rangeStart, rangeEnd, onlyAvailable, sort, from, size);

        // 1. Сохраняем статистику
        try {
            statService.saveHit(request);
        } catch (Exception e) {
            log.warn("Ошибка при сохранении статистики: {}", e.getMessage());
        }

        // 2. Нормализация параметров
        final int finalFrom = (from == null || from < 0) ? 0 : from;
        final int finalSize = (size == null || size <= 0) ? 10 : size;

        // Проверяем деление на ноль
        if (finalSize == 0) {
            return Collections.emptyList();
        }

        // Обработка текста
        final String finalText = (text == null || text.trim().isEmpty()) ? null : text.trim();

        // Обработка категорий
        final List<Long> finalCategories;
        if (categories == null || categories.isEmpty()) {
            finalCategories = null;
        } else {
            finalCategories = categories;
        }

        // Обработка дат
        LocalDateTime finalRangeStart = rangeStart;
        LocalDateTime finalRangeEnd = rangeEnd;

        // Если обе даты null, показываем будущие события
        if (finalRangeStart == null && finalRangeEnd == null) {
            finalRangeStart = LocalDateTime.now();
        }

        // Проверка диапазона дат
        if (finalRangeStart != null && finalRangeEnd != null && finalRangeStart.isAfter(finalRangeEnd)) {
            throw new ValidationException("Начальная дата не может быть позже конечной");
        }

        // Обработка onlyAvailable
        final boolean finalOnlyAvailable = (onlyAvailable == null) ? false : onlyAvailable;

        // Обработка сортировки
        final String finalSort;
        if (sort == null || sort.isEmpty()) {
            finalSort = null;
        } else if (sort.equals("EVENT_DATE") || sort.equals("VIEWS")) {
            finalSort = sort;
        } else {
            // Некорректное значение - игнорируем
            finalSort = null;
        }

        // 3. Выполнение запроса
        try {
            int page = finalFrom / finalSize;
            Pageable pageable;

            if ("VIEWS".equals(finalSort)) {
                pageable = PageRequest.of(page, finalSize);
            } else {
                // По умолчанию или при EVENT_DATE сортируем по дате
                pageable = PageRequest.of(page, finalSize, Sort.by("eventDate").ascending());
            }

            // Запрос к БД
            Page<Event> eventPage = eventRepository.findEventsPublic(
                    finalText, finalCategories, paid, finalRangeStart,
                    finalRangeEnd, finalOnlyAvailable, pageable);

            if (eventPage == null || eventPage.isEmpty()) {
                return Collections.emptyList();
            }

            List<Event> events = eventPage.getContent();

            // Получение статистики
            Map<Long, Long> viewsMap;
            try {
                viewsMap = getViewsForEvents(events);
            } catch (Exception e) {
                log.warn("Не удалось получить статистику: {}", e.getMessage());
                viewsMap = Collections.emptyMap();
            }

            // Установка просмотров и маппинг
            List<EventShortDto> result = new ArrayList<>();
            for (Event event : events) {
                try {
                    Long views = viewsMap.get(event.getId());
                    event.setViews(views != null ? views : 0L);

                    EventShortDto dto = eventMapper.toEventShortDto(event);
                    if (dto != null) {
                        result.add(dto);
                    }
                } catch (Exception e) {
                    log.warn("Ошибка при обработке события {}: {}", event.getId(), e.getMessage());
                }
            }

            // Сортировка по просмотрам если нужно
            if ("VIEWS".equals(finalSort)) {
                result.sort((dto1, dto2) -> Long.compare(dto2.getViews(), dto1.getViews()));
            }

            return result;

        } catch (ValidationException e) {
            throw e; // Пробрасываем ValidationException
        } catch (Exception e) {
            log.error("Внутренняя ошибка при получении событий: {}", e.getMessage(), e);
            // В случае любой другой ошибки возвращаем пустой список
            return Collections.emptyList();
        }
    }

    private List<EventShortDto> getEventsPublicInternal(String text, List<Long> categories, Boolean paid,
                                                        LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                                        Boolean onlyAvailable, String sort, Integer from, Integer size) {

        // Безопасная обработка параметров
        final int finalFrom;
        final int finalSize;

        try {
            // Пагинация
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

            // Обработка других параметров
            final String finalText = (text == null || text.trim().isEmpty()) ? null : text.trim();
            final List<Long> finalCategories = (categories == null || categories.isEmpty()) ? null : categories;
            final Boolean finalPaid = paid;
            final LocalDateTime finalRangeStart = rangeStart;
            final LocalDateTime finalRangeEnd = rangeEnd;
            final Boolean finalOnlyAvailable = (onlyAvailable == null) ? false : onlyAvailable;
            final String finalSort = (sort == null) ? null : sort;

            // Если даты не указаны - показываем будущие события
            LocalDateTime actualRangeStart = finalRangeStart;
            if (actualRangeStart == null && finalRangeEnd == null) {
                actualRangeStart = LocalDateTime.now();
            }

            // Создание пагинации
            int page = finalFrom / finalSize;
            Pageable pageable;

            if ("VIEWS".equals(finalSort)) {
                pageable = PageRequest.of(page, finalSize);
            } else {
                // По умолчанию сортировка по дате
                pageable = PageRequest.of(page, finalSize, Sort.by("eventDate").ascending());
            }

            // Запрос к БД
            Page<Event> eventPage = eventRepository.findEventsPublic(
                    finalText, finalCategories, finalPaid, actualRangeStart,
                    finalRangeEnd, finalOnlyAvailable, pageable);

            if (eventPage == null || eventPage.isEmpty()) {
                return Collections.emptyList();
            }

            List<Event> events = eventPage.getContent();

            // Получение статистики просмотров
            Map<Long, Long> viewsMap = getViewsForEvents(events);

            // Создание результата
            List<EventShortDto> result = new ArrayList<>();
            for (Event event : events) {
                try {
                    // Устанавливаем просмотры
                    Long views = viewsMap.get(event.getId());
                    if (views != null) {
                        event.setViews(views);
                    } else {
                        event.setViews(0L);
                    }

                    // Маппим в DTO
                    EventShortDto dto = eventMapper.toEventShortDto(event);
                    if (dto != null) {
                        result.add(dto);
                    }
                } catch (Exception e) {
                    log.warn("Ошибка при обработке события {}: {}", event.getId(), e.getMessage());
                    // Пропускаем это событие и продолжаем
                }
            }

            // Сортировка по просмотрам, если требуется
            if ("VIEWS".equals(finalSort)) {
                result.sort((a, b) -> Long.compare(b.getViews(), a.getViews()));
            }

            return result;

        } catch (Exception e) {
            log.error("Внутренняя ошибка при получении событий: {}", e.getMessage());
            return Collections.emptyList();
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
        try {
            if (events == null || events.isEmpty()) {
                return new HashMap<>();
            }

            List<String> uris = new ArrayList<>();
            for (Event event : events) {
                uris.add("/events/" + event.getId());
            }

            LocalDateTime start = LocalDateTime.now().minusYears(1);
            LocalDateTime end = LocalDateTime.now();

            List<ViewStats> stats = statsClient.getStats(start, end, uris, true);

            if (stats == null || stats.isEmpty()) {
                return new HashMap<>();
            }

            Map<Long, Long> viewsMap = new HashMap<>();
            for (ViewStats stat : stats) {
                try {
                    String uri = stat.getUri();
                    if (uri != null && uri.startsWith("/events/")) {
                        String idStr = uri.substring("/events/".length());
                        // Убираем параметры запроса, если есть
                        if (idStr.contains("?")) {
                            idStr = idStr.substring(0, idStr.indexOf('?'));
                        }
                        Long eventId = Long.parseLong(idStr);
                        viewsMap.put(eventId, stat.getHits());
                    }
                } catch (Exception e) {
                    log.debug("Не удалось обработать статистику для URI: {}", stat.getUri());
                }
            }

            return viewsMap;
        } catch (Exception e) {
            log.warn("Ошибка при получении статистики: {}", e.getMessage());
            return new HashMap<>();
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