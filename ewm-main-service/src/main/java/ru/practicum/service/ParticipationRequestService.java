package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.participation.EventRequestStatusUpdateRequest;
import ru.practicum.dto.participation.EventRequestStatusUpdateResult;
import ru.practicum.dto.participation.ParticipationRequestDto;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.mapper.RequestMapper;
import ru.practicum.model.Event;
import ru.practicum.model.ParticipationRequest;
import ru.practicum.model.User;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.ParticipationRequestRepository;
import ru.practicum.repository.UserRepository;
import ru.practicum.util.EventState;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ParticipationRequestService {

    private final ParticipationRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final RequestMapper requestMapper;

    @Transactional
    public ParticipationRequestDto createRequest(Long userId, Long eventId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        // Проверка, что пользователь не инициатор события
        if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("Инициатор не может подать заявку на участие в своем собственном мероприятии");
        }

        // Проверка, что событие опубликовано
        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Невозможно принять участие в неопубликованном мероприятии");
        }

        // Проверка, что запрос не повторный
        if (requestRepository.existsByEventIdAndRequesterId(eventId, userId)) {
            throw new ConflictException("Запрос уже существует");
        }

        // Проверка лимита участников
        long confirmedRequests = requestRepository.countConfirmedRequests(eventId);
        if (event.getParticipantLimit() > 0 && confirmedRequests >= event.getParticipantLimit()) {
            throw new ConflictException("Количество участников ограничено");
        }

        ParticipationRequest request = ParticipationRequest.builder()
                .event(event)
                .requester(user)
                .created(LocalDateTime.now())
                .status(ParticipationRequest.Status.PENDING)
                .build();

        // Если пре-модерация отключена или нет лимита, автоматически подтверждаем
        if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
            request.setStatus(ParticipationRequest.Status.CONFIRMED);
            event.setConfirmedRequests(event.getConfirmedRequests() + 1);
            eventRepository.save(event);
        }

        ParticipationRequest savedRequest = requestRepository.save(request);
        log.info("Созданный запрос на участие с id={} для проведения мероприятия id={} с пользователем id={}",
                savedRequest.getId(), eventId, userId);

        return requestMapper.toParticipationRequestDto(savedRequest);
    }

    public List<ParticipationRequestDto> getRequestsByUser(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));

        return requestRepository.findByRequesterId(userId).stream()
                .map(requestMapper::toParticipationRequestDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));

        ParticipationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Запрос с id=" + requestId + " не найден"));

        if (!request.getRequester().getId().equals(userId)) {
            throw new NotFoundException("Запрос с id=" + requestId + " не найден");
        }

        request.setStatus(ParticipationRequest.Status.CANCELED);
        ParticipationRequest canceledRequest = requestRepository.save(request);

        // Если запрос был подтвержден, уменьшаем счетчик подтвержденных заявок
        if (request.getStatus() == ParticipationRequest.Status.CONFIRMED) {
            Event event = request.getEvent();
            event.setConfirmedRequests(event.getConfirmedRequests() - 1);
            eventRepository.save(event);
        }

        return requestMapper.toParticipationRequestDto(canceledRequest);
    }

    public List<ParticipationRequestDto> getRequestsForEvent(Long userId, Long eventId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }

        return requestRepository.findByEventId(eventId).stream()
                .map(requestMapper::toParticipationRequestDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public EventRequestStatusUpdateResult updateRequestStatus(Long userId, Long eventId,
                                                              EventRequestStatusUpdateRequest updateRequest) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }

        // Проверка лимита участников
        long confirmedRequests = requestRepository.countConfirmedRequests(eventId);
        if (event.getParticipantLimit() > 0 && confirmedRequests >= event.getParticipantLimit()) {
            throw new ConflictException("Количество участников ограничено");
        }

        List<ParticipationRequest> requests = requestRepository.findPendingRequests(eventId, updateRequest.getRequestIds());

        if (requests.size() != updateRequest.getRequestIds().size()) {
            throw new ConflictException("Некоторые запросы не находятся на рассмотрении");
        }

        EventRequestStatusUpdateResult result = new EventRequestStatusUpdateResult();

        if (updateRequest.getStatus() == EventRequestStatusUpdateRequest.Status.CONFIRMED) {
            // Подтверждение запросов
            List<ParticipationRequestDto> confirmedDtos = new ArrayList<>();
            List<ParticipationRequestDto> rejectedDtos = new ArrayList<>();

            for (ParticipationRequest request : requests) {
                if (event.getParticipantLimit() == 0 || confirmedRequests < event.getParticipantLimit()) {
                    request.setStatus(ParticipationRequest.Status.CONFIRMED);
                    confirmedRequests++;
                    confirmedDtos.add(requestMapper.toParticipationRequestDto(request));
                } else {
                    request.setStatus(ParticipationRequest.Status.REJECTED);
                    rejectedDtos.add(requestMapper.toParticipationRequestDto(request));
                }
            }

            requestRepository.saveAll(requests);
            event.setConfirmedRequests((int) confirmedRequests);
            eventRepository.save(event);

            // Если после подтверждения лимит достигнут, отклоняем остальные
            if (event.getParticipantLimit() > 0 && confirmedRequests >= event.getParticipantLimit()) {
                List<ParticipationRequest> pendingRequests = requestRepository.findByEventIdAndStatus(
                        eventId, ParticipationRequest.Status.PENDING);
                pendingRequests.forEach(req -> req.setStatus(ParticipationRequest.Status.REJECTED));
                requestRepository.saveAll(pendingRequests);

                rejectedDtos.addAll(pendingRequests.stream()
                        .map(requestMapper::toParticipationRequestDto)
                        .collect(Collectors.toList()));
            }

            result.setConfirmedRequests(confirmedDtos);
            result.setRejectedRequests(rejectedDtos);
        } else {
            // Отклонение запросов
            requests.forEach(req -> req.setStatus(ParticipationRequest.Status.REJECTED));
            requestRepository.saveAll(requests);

            result.setConfirmedRequests(List.of());
            result.setRejectedRequests(requests.stream()
                    .map(requestMapper::toParticipationRequestDto)
                    .collect(Collectors.toList()));
        }

        return result;
    }
}
