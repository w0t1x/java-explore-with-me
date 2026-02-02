package ru.practicum.statsserver;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ApiError {
    private List<String> errors;
    private String message;
    private String reason;
    private String status;
    private String timestamp;
}
