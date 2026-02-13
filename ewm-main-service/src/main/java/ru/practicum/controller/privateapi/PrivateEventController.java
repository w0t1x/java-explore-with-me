package ru.practicum.controller.privateapi;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.event.EventFullDto;
import ru.practicum.dto.event.EventShortDto;
import ru.practicum.dto.event.NewEventDto;
import ru.practicum.dto.event.UpdateEventUserRequest;
import ru.practicum.dto.request.EventRequestStatusUpdateRequest;
import ru.practicum.dto.request.EventRequestStatusUpdateResult;
import ru.practicum.dto.request.ParticipationRequestDto;
import ru.practicum.service.EventService;
import ru.practicum.service.RequestService;

import java.util.List;

@RestController
@RequestMapping("/users/{userId}/events")
@RequiredArgsConstructor
@Validated
public class PrivateEventController {

    private final EventService eventService;
    private final RequestService requestService;

    @GetMapping
    public List<EventShortDto> getEvents(@PathVariable long userId,
                                         @RequestParam(defaultValue = "0") @PositiveOrZero int from,
                                         @RequestParam(defaultValue = "10") @Positive int size) {
        return eventService.getUserEvents(userId, from, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventFullDto create(@PathVariable long userId,
                               @RequestBody @Valid NewEventDto dto) {
        return eventService.createEvent(userId, dto);
    }

    @GetMapping("/{eventId}")
    public EventFullDto get(@PathVariable long userId,
                            @PathVariable long eventId) {
        return eventService.getUserEvent(userId, eventId);
    }

    @PatchMapping("/{eventId}")
    public EventFullDto update(@PathVariable long userId,
                               @PathVariable long eventId,
                               @RequestBody @Valid UpdateEventUserRequest dto) {
        return eventService.updateUserEvent(userId, eventId, dto);
    }

    @GetMapping("/{eventId}/requests")
    public List<ParticipationRequestDto> getParticipants(@PathVariable long userId,
                                                         @PathVariable long eventId) {
        return requestService.getEventParticipants(userId, eventId);
    }

    @PatchMapping("/{eventId}/requests")
    public EventRequestStatusUpdateResult changeStatus(@PathVariable long userId,
                                                       @PathVariable long eventId,
                                                       @RequestBody @Valid EventRequestStatusUpdateRequest dto) {
        return requestService.changeStatus(userId, eventId, dto);
    }
}
