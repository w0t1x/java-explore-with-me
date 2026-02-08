package ru.practicum.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

import static ru.practicum.util.DateTimeUtil.ISO_MILLIS_PATTERN;

@Data
@Builder
public class ParticipationRequestDto {
    @JsonFormat(pattern = ISO_MILLIS_PATTERN)
    private LocalDateTime created;
    private Long event;
    private Long id;
    private Long requester;
    private String status;
}
