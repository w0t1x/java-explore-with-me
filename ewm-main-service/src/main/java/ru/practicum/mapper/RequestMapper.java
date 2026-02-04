package ru.practicum.mapper;

import org.springframework.stereotype.Component;
import ru.practicum.dto.participation.ParticipationRequestDto;
import ru.practicum.model.ParticipationRequest;

@Component
public class RequestMapper {

    public ParticipationRequestDto toParticipationRequestDto(ParticipationRequest request) {
        return ParticipationRequestDto.builder()
                .id(request.getId())
                .created(request.getCreated())
                .event(request.getEvent().getId())
                .requester(request.getRequester().getId())
                .status(request.getStatus().name())
                .build();
    }
}
