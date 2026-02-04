package ru.practicum.dto.compilation;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCompilationRequest {

    private Set<Long> events;
    private Boolean pinned;

    @Size(min = 1, max = 50, message = "Заголовок должен содержать от 1 до 50 символов")
    private String title;
}
