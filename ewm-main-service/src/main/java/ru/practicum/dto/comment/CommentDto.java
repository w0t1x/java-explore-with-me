package ru.practicum.dto.comment;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.practicum.dto.user.UserShortDto;
import ru.practicum.model.CommentStatus;

import java.time.LocalDateTime;

import static ru.practicum.util.DateTimeUtil.EWM_DATE_TIME_PATTERN;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentDto {
    private Long id;
    private String text;
    private Long eventId;
    private UserShortDto author;
    private CommentStatus status;

    @JsonFormat(pattern = EWM_DATE_TIME_PATTERN)
    private LocalDateTime createdOn;

    @JsonFormat(pattern = EWM_DATE_TIME_PATTERN)
    private LocalDateTime updatedOn;

    @JsonFormat(pattern = EWM_DATE_TIME_PATTERN)
    private LocalDateTime moderatedOn;

    private String rejectionReason;
}
