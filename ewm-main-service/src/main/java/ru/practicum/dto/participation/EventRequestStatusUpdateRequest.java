package ru.practicum.dto.participation;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventRequestStatusUpdateRequest {

    @NotNull(message = "Идентификаторы запросов не могут быть пустыми")
    private List<Long> requestIds;

    @NotNull(message = "Статус не может быть нулевым")
    private Status status;

    public enum Status {
        CONFIRMED, REJECTED
    }
}
