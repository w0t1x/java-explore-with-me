package ru.practicum.mapper;

import ru.practicum.dto.event.EventFullDto;
import ru.practicum.dto.event.EventShortDto;
import ru.practicum.dto.event.NewEventDto;
import ru.practicum.model.Category;
import ru.practicum.model.Event;
import ru.practicum.model.EventState;
import ru.practicum.model.User;

import java.time.LocalDateTime;

public final class EventMapper {
    private EventMapper() {}

    public static Event toEntity(NewEventDto dto, Category category, User initiator) {
        return Event.builder()
                .title(dto.getTitle())
                .annotation(dto.getAnnotation())
                .description(dto.getDescription())
                .category(category)
                .initiator(initiator)
                .location(LocationMapper.toEntity(dto.getLocation()))
                .paid(Boolean.TRUE.equals(dto.getPaid()))
                .participantLimit(dto.getParticipantLimit() == null ? 0 : dto.getParticipantLimit())
                .requestModeration(dto.getRequestModeration() == null || dto.getRequestModeration())
                .state(EventState.PENDING)
                .eventDate(dto.getEventDate())
                .createdOn(LocalDateTime.now())
                .build();
    }

    public static EventShortDto toShortDto(Event entity, long confirmed, long views) {
        return EventShortDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .annotation(entity.getAnnotation())
                .category(CategoryMapper.toDto(entity.getCategory()))
                .initiator(UserMapper.toShortDto(entity.getInitiator()))
                .eventDate(entity.getEventDate())
                .paid(entity.isPaid())
                .confirmedRequests(confirmed)
                .views(views)
                .build();
    }

    public static EventFullDto toFullDto(Event entity, long confirmed, long views) {
        return EventFullDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .annotation(entity.getAnnotation())
                .description(entity.getDescription())
                .category(CategoryMapper.toDto(entity.getCategory()))
                .initiator(UserMapper.toShortDto(entity.getInitiator()))
                .location(LocationMapper.toDto(entity.getLocation()))
                .eventDate(entity.getEventDate())
                .createdOn(entity.getCreatedOn())
                .publishedOn(entity.getPublishedOn())
                .paid(entity.isPaid())
                .participantLimit(entity.getParticipantLimit())
                .requestModeration(entity.isRequestModeration())
                .state(entity.getState())
                .confirmedRequests(confirmed)
                .views(views)
                .build();
    }
}
