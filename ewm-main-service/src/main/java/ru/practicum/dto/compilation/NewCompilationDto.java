package ru.practicum.dto.compilation;

import jakarta.validation.constraints.NotBlank;
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
public class NewCompilationDto {

    private Set<Long> events;
    private Boolean pinned = false;

    @NotBlank(message = "Заголовок не может быть пустым")
    @Size(min = 1, max = 50, message = "Заголовок должен содержать от 1 до 50 символов")
    private String title;
}
