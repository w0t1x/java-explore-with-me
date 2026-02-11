package ru.practicum.dto.location;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LocationDto {
    @NotNull
    private Float lat;

    @NotNull
    private Float lon;
}
