package ru.practicum.dto.category;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewCategoryDto {

    @NotBlank(message = "Имя не может быть пустым")
    @Size(min = 1, max = 50, message = "Длина имени должна составлять от 1 до 50 символов")
    private String name;
}
