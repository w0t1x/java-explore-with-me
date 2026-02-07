package ru.practicum.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.practicum.dto.event.EventFullDto;
import ru.practicum.dto.event.EventShortDto;
import ru.practicum.dto.event.NewEventDto;
import ru.practicum.dto.location.LocationDto;
import ru.practicum.model.Event;
import ru.practicum.model.Location;
import ru.practicum.util.EventState;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class EventMapper {

    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;

    public Event toEvent(NewEventDto newEventDto) {
        if (newEventDto == null) {
            throw new IllegalArgumentException("NewEventDto не может быть null");
        }

        return Event.builder()
                .annotation(newEventDto.getAnnotation() != null ? newEventDto.getAnnotation() : "")
                .description(newEventDto.getDescription() != null ? newEventDto.getDescription() : "")
                .eventDate(newEventDto.getEventDate())
                .location(newEventDto.getLocation() != null ?
                        Location.builder()
                                .lat(newEventDto.getLocation().getLat())
                                .lon(newEventDto.getLocation().getLon())
                                .build() :
                        Location.builder().lat(0.0).lon(0.0).build())
                .paid(newEventDto.getPaid() != null ? newEventDto.getPaid() : false)
                .participantLimit(newEventDto.getParticipantLimit() != null ? newEventDto.getParticipantLimit() : 0)
                .requestModeration(newEventDto.getRequestModeration() != null ? newEventDto.getRequestModeration() : true)
                .title(newEventDto.getTitle() != null ? newEventDto.getTitle() : "")
                .build();
    }

    public EventFullDto toEventFullDto(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("Event не может быть null");
        }

        // Создаем DTO с правильной логикой для publishedOn
        EventFullDto dto = EventFullDto.builder()
                .id(event.getId())
                .annotation(event.getAnnotation() != null ? event.getAnnotation() : "")
                .category(event.getCategory() != null ? categoryMapper.toCategoryDto(event.getCategory()) : null)
                .confirmedRequests(event.getConfirmedRequests() != null ? event.getConfirmedRequests() : 0)
                .createdOn(event.getCreatedOn() != null ? event.getCreatedOn() : LocalDateTime.now())
                .description(event.getDescription() != null ? event.getDescription() : "")
                .eventDate(event.getEventDate() != null ? event.getEventDate() : LocalDateTime.now().plusDays(1))
                .initiator(event.getInitiator() != null ? userMapper.toUserShortDto(event.getInitiator()) : null)
                .location(event.getLocation() != null ?
                        LocationDto.builder()
                                .lat(event.getLocation().getLat())
                                .lon(event.getLocation().getLon())
                                .build() :
                        LocationDto.builder().lat(0.0).lon(0.0).build())
                .paid(event.getPaid() != null ? event.getPaid() : false)
                .participantLimit(event.getParticipantLimit() != null ? event.getParticipantLimit() : 0)
                .publishedOn(event.getState() == EventState.PUBLISHED ?
                        (event.getPublishedOn() != null ? event.getPublishedOn() : event.getCreatedOn())
                        : null)
                .requestModeration(event.getRequestModeration() != null ? event.getRequestModeration() : true)
                .state(event.getState() != null ? event.getState() : EventState.PENDING)
                .title(event.getTitle() != null ? event.getTitle() : "")
                .views(event.getViews() != null ? event.getViews() : 0L)
                .build();

        validateEventFullDto(dto);
        return dto;
    }

    private void validateEventFullDto(EventFullDto dto) {
        if (dto.getId() == null) throw new IllegalArgumentException("ID события не может быть null");
        if (dto.getAnnotation() == null) throw new IllegalArgumentException("Annotation не может быть null");
        if (dto.getCategory() == null) throw new IllegalArgumentException("Category не может быть null");
        if (dto.getEventDate() == null) throw new IllegalArgumentException("EventDate не может быть null");
        if (dto.getInitiator() == null) throw new IllegalArgumentException("Initiator не может быть null");
        if (dto.getLocation() == null) throw new IllegalArgumentException("Location не может быть null");
        if (dto.getTitle() == null) throw new IllegalArgumentException("Title не может быть null");
        if (dto.getCreatedOn() == null) throw new IllegalArgumentException("CreatedOn не может быть null");
        if (dto.getState() == null) throw new IllegalArgumentException("State не может быть null");
        if (dto.getPaid() == null) throw new IllegalArgumentException("Paid не может быть null");
        if (dto.getParticipantLimit() == null) throw new IllegalArgumentException("ParticipantLimit не может быть null");
        if (dto.getRequestModeration() == null) throw new IllegalArgumentException("RequestModeration не может быть null");
        if (dto.getConfirmedRequests() == null) throw new IllegalArgumentException("ConfirmedRequests не может быть null");
        if (dto.getViews() == null) throw new IllegalArgumentException("Views не может быть null");
    }

    public EventShortDto toEventShortDto(Event event) {
        if (event == null) {
            return null;
        }

        try {
            return EventShortDto.builder()
                    .id(event.getId())
                    .annotation(event.getAnnotation() != null ? event.getAnnotation() : "")
                    .category(event.getCategory() != null ? categoryMapper.toCategoryDto(event.getCategory()) : null)
                    .confirmedRequests(event.getConfirmedRequests() != null ? event.getConfirmedRequests() : 0)
                    .eventDate(event.getEventDate() != null ? event.getEventDate() : LocalDateTime.now())
                    .initiator(event.getInitiator() != null ? userMapper.toUserShortDto(event.getInitiator()) : null)
                    .paid(event.getPaid() != null ? event.getPaid() : false)
                    .title(event.getTitle() != null ? event.getTitle() : "")
                    .views(event.getViews() != null ? event.getViews() : 0L)
                    .build();
        } catch (Exception e) {
            return null;
        }
    }

    private void validateEventShortDto(EventShortDto dto) {
        if (dto.getId() == null) throw new IllegalArgumentException("ID события не может быть null");
        if (dto.getAnnotation() == null) throw new IllegalArgumentException("Annotation не может быть null");
        if (dto.getCategory() == null) throw new IllegalArgumentException("Category не может быть null");
        if (dto.getEventDate() == null) throw new IllegalArgumentException("EventDate не может быть null");
        if (dto.getInitiator() == null) throw new IllegalArgumentException("Initiator не может быть null");
        if (dto.getTitle() == null) throw new IllegalArgumentException("Title не может быть null");
    }
}