package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.request.*;
import ru.practicum.exception.BadRequestException;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.mapper.RequestMapper;
import ru.practicum.model.*;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.ParticipationRequestRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequestService {

    private final ParticipationRequestRepository requestRepository;
    private final EventRepository eventRepository;
    private final UserService userService;

    public List<ParticipationRequestDto> getUserRequests(long userId) {
        userService.getOrThrow(userId);
        return requestRepository.findAllByRequesterId(userId).stream()
                .map(RequestMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ParticipationRequestDto addRequest(long userId, long eventId) {
        User requester = userService.getOrThrow(userId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("Initiator cannot make request to own event");
        }

        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Event must be published");
        }

        if (requestRepository.existsByRequesterIdAndEventId(userId, eventId)) {
            throw new ConflictException("Request already exists");
        }

        long confirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        if (event.getParticipantLimit() != 0 && confirmed >= event.getParticipantLimit()) {
            throw new ConflictException("The participant limit has been reached");
        }

        RequestStatus status = RequestStatus.PENDING;
        if (!event.isRequestModeration() || event.getParticipantLimit() == 0) {
            status = RequestStatus.CONFIRMED;
        }

        ParticipationRequest pr = ParticipationRequest.builder()
                .created(LocalDateTime.now())
                .event(event)
                .requester(requester)
                .status(status)
                .build();
        ParticipationRequest saved = requestRepository.save(pr);
        return RequestMapper.toDto(saved);
    }

    @Transactional
    public ParticipationRequestDto cancel(long userId, long requestId) {
        userService.getOrThrow(userId);
        ParticipationRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request with id=" + requestId + " was not found"));
        if (!req.getRequester().getId().equals(userId)) {
            throw new ConflictException("Only requester can cancel request");
        }
        req.setStatus(RequestStatus.CANCELED);
        return RequestMapper.toDto(requestRepository.save(req));
    }

    public List<ParticipationRequestDto> getEventParticipants(long userId, long eventId) {
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        return requestRepository.findAllByEventId(event.getId()).stream()
                .map(RequestMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public EventRequestStatusUpdateResult changeStatus(long userId, long eventId, EventRequestStatusUpdateRequest update) {
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        List<ParticipationRequest> requests = requestRepository.findAllByEventIdAndIdIn(eventId, update.getRequestIds());
        if (requests.size() != update.getRequestIds().size()) {
            // some request not found under event
            throw new NotFoundException("Request was not found");
        }

        List<ParticipationRequestDto> confirmedDtos = new ArrayList<>();
        List<ParticipationRequestDto> rejectedDtos = new ArrayList<>();

        long confirmedCount = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);

        for (ParticipationRequest r : requests) {
            if (r.getStatus() != RequestStatus.PENDING) {
                throw new BadRequestException("Request must have status PENDING");
            }
            if (update.getStatus() == RequestUpdateStatus.CONFIRMED) {
                if (event.getParticipantLimit() != 0 && confirmedCount >= event.getParticipantLimit()) {
                    throw new ConflictException("The participant limit has been reached");
                }
                r.setStatus(RequestStatus.CONFIRMED);
                confirmedCount++;
                confirmedDtos.add(RequestMapper.toDto(r));
            } else {
                r.setStatus(RequestStatus.REJECTED);
                rejectedDtos.add(RequestMapper.toDto(r));
            }
        }

        requestRepository.saveAll(requests);

        if (update.getStatus() == RequestUpdateStatus.CONFIRMED
                && event.getParticipantLimit() != 0
                && confirmedCount >= event.getParticipantLimit()) {
            // reject all the remaining pending requests for this event
            List<ParticipationRequest> pending = requestRepository.findAllByEventId(eventId).stream()
                    .filter(r -> r.getStatus() == RequestStatus.PENDING)
                    .collect(Collectors.toList());
            for (ParticipationRequest p : pending) {
                p.setStatus(RequestStatus.REJECTED);
            }
            requestRepository.saveAll(pending);
            rejectedDtos.addAll(pending.stream().map(RequestMapper::toDto).toList());
        }

        return EventRequestStatusUpdateResult.builder()
                .confirmedRequests(confirmedDtos)
                .rejectedRequests(rejectedDtos)
                .build();
    }
}
