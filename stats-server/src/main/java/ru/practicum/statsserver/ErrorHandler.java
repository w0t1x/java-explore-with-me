package ru.practicum.statsserver;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

@RestControllerAdvice
@Slf4j
public class ErrorHandler {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String BAD_REQUEST_REASON = "Incorrectly made request.";
    private static final String INTERNAL_ERROR_REASON = "Internal server error.";

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleRequestParamExceptions(Exception e) {
        log.error("Ошибка параметров запроса: {}", e.getMessage());
        return ApiError.builder()
                .errors(Collections.emptyList())
                .status(HttpStatus.BAD_REQUEST.name())
                .reason(BAD_REQUEST_REASON)
                .message(e.getMessage())
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.error("Ошибка чтения тела запроса: {}", e.getMessage());
        return ApiError.builder()
                .errors(Collections.emptyList())
                .status(HttpStatus.BAD_REQUEST.name())
                .reason(BAD_REQUEST_REASON)
                .message(e.getMessage())
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("Ошибка валидации: {}", e.getMessage());
        return ApiError.builder()
                .errors(Collections.emptyList())
                .status(HttpStatus.BAD_REQUEST.name())
                .reason(BAD_REQUEST_REASON)
                .message(e.getMessage())
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidationExceptions(Exception e) {
        log.error("Ошибка валидации данных: {}", e.getMessage());
        return ApiError.builder()
                .errors(Collections.emptyList())
                .status(HttpStatus.BAD_REQUEST.name())
                .reason(BAD_REQUEST_REASON)
                .message(e.getMessage())
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleException(Exception e) {
        log.error("Внутренняя ошибка сервера: {}", e.getMessage(), e);
        return ApiError.builder()
                .errors(Collections.emptyList())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.name())
                .reason(INTERNAL_ERROR_REASON)
                .message(e.getMessage())
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();
    }
}
