package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.event.*;
import ru.practicum.exception.BadRequestException;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.mapper.EventMapper;
import ru.practicum.model.*;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.ParticipationRequestRepository;
import ru.practicum.repository.spec.EventSpecifications;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static ru.practicum.util.PageableUtil.fromOffset;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final UserService userService;
    private final CategoryService categoryService;
    private final StatsService statsService;
    private final ParticipationRequestRepository requestRepository;

    // --- Public ---
    public List<EventShortDto> publicSearch(String text, List<Long> categories, Boolean paid,
                                            LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                            boolean onlyAvailable, EventSort sort,
                                            int from, int size) {
        if (rangeStart != null && rangeEnd != null && rangeEnd.isBefore(rangeStart)) {
            throw new BadRequestException("rangeEnd must not be before rangeStart");
        }
        if (rangeStart == null && rangeEnd == null) {
            rangeStart = LocalDateTime.now();
        }

        Specification<Event> spec = Specification.where(EventSpecifications.isPublished())
                .and(EventSpecifications.textSearch(text))
                .and(EventSpecifications.categoryIn(categories))
                .and(EventSpecifications.paidEq(paid))
                .and(EventSpecifications.dateAfter(rangeStart))
                .and(EventSpecifications.dateBefore(rangeEnd))
                .and(EventSpecifications.onlyAvailable(onlyAvailable));

        Sort dbSort = Sort.by("eventDate").ascending();
        if (sort == EventSort.EVENT_DATE || sort == null) {
            dbSort = Sort.by("eventDate").ascending();
        } else if (sort == EventSort.VIEWS) {
            // can't sort by views in DB; sort after enrichment
            dbSort = Sort.by("eventDate").ascending();
        }

        Pageable pageable = fromOffset(from, size, dbSort);
        List<Event> events = eventRepository.findAll(spec, pageable).getContent();

        Map<Long, Long> confirmed = getConfirmedCounts(events);
        Map<Long, Long> views = statsService.getViewsForEvents(events);

        List<EventShortDto> dtos = events.stream()
                .map(e -> EventMapper.toShortDto(e,
                        confirmed.getOrDefault(e.getId(), 0L),
                        views.getOrDefault(e.getId(), 0L)))
                .collect(Collectors.toList());

        if (sort == EventSort.VIEWS) {
            dtos.sort(Comparator.comparingLong((EventShortDto e) -> Optional.ofNullable(e.getViews()).orElse(0L)).reversed());
        }

        return dtos;
    }

    public EventFullDto publicGet(long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }
        long confirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        long views = statsService.getViewsForEvent(eventId);
        return EventMapper.toFullDto(event, confirmed, views);
    }

    // --- Private ---
    public List<EventShortDto> getUserEvents(long userId, int from, int size) {
        userService.getOrThrow(userId);
        Pageable pageable = fromOffset(from, size, Sort.by("id").ascending());
        List<Event> events = eventRepository.findAllByInitiatorId(userId, pageable).getContent();
        Map<Long, Long> confirmed = getConfirmedCounts(events);
        Map<Long, Long> views = statsService.getViewsForEvents(events);
        return events.stream()
                .map(e -> EventMapper.toShortDto(e,
                        confirmed.getOrDefault(e.getId(), 0L),
                        views.getOrDefault(e.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Transactional
    public EventFullDto createEvent(long userId, NewEventDto dto) {
        User user = userService.getOrThrow(userId);

        if (dto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new BadRequestException("Field: eventDate. Error: должно содержать дату, которая еще не наступила. Value: " + dto.getEventDate());
        }

        Category category = categoryService.getEntity(dto.getCategory());
        Event event = EventMapper.toEntity(dto, category, user);
        Event saved = eventRepository.save(event);
        return EventMapper.toFullDto(saved, 0L, 0L);
    }

    public EventFullDto getUserEvent(long userId, long eventId) {
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
        long confirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        long views = statsService.getViewsForEvent(eventId);
        return EventMapper.toFullDto(event, confirmed, views);
    }

    @Transactional
    public EventFullDto updateUserEvent(long userId, long eventId, UpdateEventUserRequest dto) {
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        if (!(event.getState() == EventState.PENDING || event.getState() == EventState.CANCELED)) {
            throw new ConflictException("Only pending or canceled events can be changed");
        }

        if (dto.getEventDate() != null && dto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new BadRequestException("Field: eventDate. Error: должно содержать дату, которая еще не наступила. Value: " + dto.getEventDate());
        }

        applyUpdate(event, dto.getTitle(), dto.getAnnotation(), dto.getDescription(), dto.getCategory(),
                dto.getLocation(), dto.getPaid(), dto.getParticipantLimit(), dto.getRequestModeration(),
                dto.getEventDate());

        if (dto.getStateAction() != null) {
            if (dto.getStateAction() == UserStateAction.SEND_TO_REVIEW) {
                event.setState(EventState.PENDING);
            } else if (dto.getStateAction() == UserStateAction.CANCEL_REVIEW) {
                event.setState(EventState.CANCELED);
            }
        }

        Event saved = eventRepository.save(event);
        long confirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        long views = statsService.getViewsForEvent(eventId);
        return EventMapper.toFullDto(saved, confirmed, views);
    }

    // --- Admin ---
    public List<EventFullDto> adminSearch(List<Long> users, List<EventState> states, List<Long> categories,
                                          LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                          int from, int size) {
        if (rangeStart != null && rangeEnd != null && rangeEnd.isBefore(rangeStart)) {
            throw new BadRequestException("rangeEnd must not be before rangeStart");
        }
        Specification<Event> spec = Specification.where(EventSpecifications.initiatorIn(users))
                .and(EventSpecifications.stateIn(states))
                .and(EventSpecifications.categoryIn(categories))
                .and(EventSpecifications.dateAfter(rangeStart))
                .and(EventSpecifications.dateBefore(rangeEnd));

        Pageable pageable = fromOffset(from, size, Sort.by("id").ascending());
        List<Event> events = eventRepository.findAll(spec, pageable).getContent();

        Map<Long, Long> confirmed = getConfirmedCounts(events);
        Map<Long, Long> views = statsService.getViewsForEvents(events);

        return events.stream()
                .map(e -> EventMapper.toFullDto(e,
                        confirmed.getOrDefault(e.getId(), 0L),
                        views.getOrDefault(e.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Transactional
    public EventFullDto adminUpdate(long eventId, UpdateEventAdminRequest dto) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        if (dto.getEventDate() != null && event.getPublishedOn() != null
                && dto.getEventDate().isBefore(event.getPublishedOn().plusHours(1))) {
            throw new ConflictException("Event date must be at least 1 hour after publication");
        }

        if (dto.getEventDate() != null && dto.getEventDate().isBefore(LocalDateTime.now())) {
            throw new BadRequestException(
                    "Field: eventDate. Error: должно содержать дату, которая еще не наступила. Value: " + dto.getEventDate()
            );
        }

        applyUpdate(event, dto.getTitle(), dto.getAnnotation(), dto.getDescription(), dto.getCategory(),
                dto.getLocation(), dto.getPaid(), dto.getParticipantLimit(), dto.getRequestModeration(),
                dto.getEventDate());

        if (dto.getStateAction() != null) {
            if (dto.getStateAction() == AdminStateAction.PUBLISH_EVENT) {
                if (event.getState() != EventState.PENDING) {
                    throw new ConflictException("Cannot publish the event because it's not in the right state: " + event.getState());
                }
                if (event.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
                    throw new ConflictException("Event date must be at least 1 hour after publication");
                }
                event.setState(EventState.PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());
            } else if (dto.getStateAction() == AdminStateAction.REJECT_EVENT) {
                if (event.getState() == EventState.PUBLISHED) {
                    throw new ConflictException("Cannot reject the event because it's already published");
                }
                event.setState(EventState.CANCELED);
            }
        }

        Event saved = eventRepository.save(event);
        long confirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        long views = statsService.getViewsForEvent(eventId);
        return EventMapper.toFullDto(saved, confirmed, views);
    }

    // --- helpers ---
    private void applyUpdate(Event event,
                             String title,
                             String annotation,
                             String description,
                             Long categoryId,
                             ru.practicum.dto.location.LocationDto location,
                             Boolean paid,
                             Integer participantLimit,
                             Boolean requestModeration,
                             LocalDateTime eventDate) {
        if (title != null) event.setTitle(title);
        if (annotation != null) event.setAnnotation(annotation);
        if (description != null) event.setDescription(description);
        if (categoryId != null) event.setCategory(categoryService.getEntity(categoryId));
        if (location != null) event.setLocation(ru.practicum.mapper.LocationMapper.toEntity(location));
        if (paid != null) event.setPaid(paid);
        if (participantLimit != null) event.setParticipantLimit(participantLimit);
        if (requestModeration != null) event.setRequestModeration(requestModeration);
        if (eventDate != null) event.setEventDate(eventDate);
    }

    private Map<Long, Long> getConfirmedCounts(Collection<Event> events) {
        if (events == null || events.isEmpty()) return Map.of();
        List<Long> ids = events.stream().map(Event::getId).toList();
        Map<Long, Long> map = new HashMap<>();
        for (ParticipationRequestRepository.ConfirmedCount cc :
                requestRepository.countByEventIdsAndStatus(ids, RequestStatus.CONFIRMED)) {
            map.put(cc.getEventId(), cc.getCnt());
        }
        return map;
    }
}
