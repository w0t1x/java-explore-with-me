package ru.practicum.statsserver;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.springframework.boot.jackson.JsonComponent;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

@JsonComponent
public class LocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    private static final DateTimeFormatter FORMATTER =
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd[ ]['T']HH:mm:ss")
                    .toFormatter();

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
        String date = p.getText();
        // Пробуем оба формата
        if (date.contains("T")) {
            return LocalDateTime.parse(date, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } else {
            return LocalDateTime.parse(date,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }
}