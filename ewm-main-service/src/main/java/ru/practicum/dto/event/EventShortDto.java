package ru.practicum.dto.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;
import ru.practicum.dto.category.CategoryDto;
import ru.practicum.dto.user.UserShortDto;

import java.time.LocalDateTime;

import static ru.practicum.util.DateTimeUtil.EWM_DATE_TIME_PATTERN;

@Data
@Builder
public class EventShortDto {
    private String annotation;
    private CategoryDto category;
    private Long confirmedRequests;

    @JsonFormat(pattern = EWM_DATE_TIME_PATTERN)
    private LocalDateTime eventDate;

    private Long id;
    private UserShortDto initiator;
    private boolean paid;
    private String title;
    private Long views;
}
