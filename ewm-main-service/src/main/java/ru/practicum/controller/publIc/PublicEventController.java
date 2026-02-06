package ru.practicum.controller.publIc;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.event.EventFullDto;
import ru.practicum.dto.event.EventShortDto;
import ru.practicum.service.EventService;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@Validated
@RequestMapping("/events")
public class PublicEventController {

    private final EventService eventService;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<EventShortDto> getEvents(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) List<Long> categories,
            @RequestParam(required = false) Boolean paid,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeStart,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeEnd,
            @RequestParam(required = false, defaultValue = "false") Boolean onlyAvailable,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") @PositiveOrZero Integer from,
            @RequestParam(defaultValue = "10") @Positive Integer size,
            HttpServletRequest request) {

        try {
            // Преобразуем пустую строку в null
            if (text != null && text.trim().isEmpty()) {
                text = null;
            }

            // Проверяем сортировку
            if (sort != null && !sort.isEmpty()) {
                if (!sort.equals("EVENT_DATE") && !sort.equals("VIEWS")) {
                    sort = null; // Игнорируем некорректное значение
                }
            } else {
                sort = null;
            }

            // Проверяем пагинацию
            if (from < 0) from = 0;
            if (size <= 0) size = 10;

            return eventService.getEventsPublic(text, categories, paid, rangeStart, rangeEnd,
                    onlyAvailable, sort, from, size, request);
        } catch (Exception e) {
            log.error("Ошибка в контроллере при запросе событий: {}", e.getMessage());
            // Возвращаем пустой список вместо выбрасывания исключения
            return Collections.emptyList();
        }
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public EventFullDto getEvent(@PathVariable Long id, HttpServletRequest request) {
        log.info("GET /events/{} получен запрос", id);
        return eventService.getEventPublic(id, request);
    }
}