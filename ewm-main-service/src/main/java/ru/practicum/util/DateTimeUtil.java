package ru.practicum.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DateTimeUtil {
    private DateTimeUtil() {
    }

    public static final String EWM_DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final DateTimeFormatter EWM_FORMATTER = DateTimeFormatter.ofPattern(EWM_DATE_TIME_PATTERN);

    public static final String ISO_MILLIS_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    public static final DateTimeFormatter ISO_MILLIS_FORMATTER = DateTimeFormatter.ofPattern(ISO_MILLIS_PATTERN);

    public static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
}
