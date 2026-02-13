package ru.practicum.mapper;

import ru.practicum.dto.compilation.CompilationDto;
import ru.practicum.dto.event.EventShortDto;
import ru.practicum.model.Compilation;

import java.util.List;

public final class CompilationMapper {
    private CompilationMapper() {
    }

    public static CompilationDto toDto(Compilation entity, List<EventShortDto> events) {
        return CompilationDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .pinned(entity.isPinned())
                .events(events)
                .build();
    }
}
