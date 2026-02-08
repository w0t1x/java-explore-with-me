package ru.practicum.mapper;

import ru.practicum.dto.common.LocationDto;
import ru.practicum.model.Location;

public final class LocationMapper {
    private LocationMapper() {}

    public static Location toEntity(LocationDto dto) {
        if (dto == null) return null;
        return Location.builder()
                .lat(dto.getLat())
                .lon(dto.getLon())
                .build();
    }

    public static LocationDto toDto(Location entity) {
        if (entity == null) return null;
        return LocationDto.builder()
                .lat(entity.getLat())
                .lon(entity.getLon())
                .build();
    }
}
