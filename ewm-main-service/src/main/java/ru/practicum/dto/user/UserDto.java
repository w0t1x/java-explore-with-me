package ru.practicum.dto.user;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    private Long id;

    @NotBlank(message = "Имя не может быть пустым")
    @Size(min = 2, max = 250, message = "Длина имени должна составлять от 2 до 250 символов")
    private String name;

    @NotBlank(message = "Email не может быть пустым")
    @Email(message = "Email должно быть действительным")
    @Size(min = 6, max = 254, message = "Email должно быть от 6 до 254 символов")
    private String email;
}
