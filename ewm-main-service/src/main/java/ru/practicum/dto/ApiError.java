package ru.practicum.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiError {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private List<String> errors;
    private String message;
    private String reason;
    private String status;
    private String timestamp;

    public static ApiError create(String message, String reason, String status) {
        return ApiError.builder()
                .message(message)
                .reason(reason)
                .status(status)
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();
    }
}
