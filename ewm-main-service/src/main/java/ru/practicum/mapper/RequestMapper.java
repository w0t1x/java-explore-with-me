package ru.practicum.mapper;

import ru.practicum.dto.request.ParticipationRequestDto;
import ru.practicum.model.ParticipationRequest;

public final class RequestMapper {
    private RequestMapper() {}

    public static ParticipationRequestDto toDto(ParticipationRequest entity) {
        return ParticipationRequestDto.builder()
                .id(entity.getId())
                .created(entity.getCreated())
                .event(entity.getEvent().getId())
                .requester(entity.getRequester().getId())
                .status(entity.getStatus().name())
                .build();
    }
}
