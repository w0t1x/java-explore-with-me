package ru.practicum.dto.location;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationDto {

    @NotNull(message = "Широта не может быть нулевой")
    private Double lat;

    @NotNull(message = "Долгота не может быть нулевой")
    private Double lon;
}
