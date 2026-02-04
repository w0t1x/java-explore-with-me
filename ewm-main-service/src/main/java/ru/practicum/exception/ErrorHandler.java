package ru.practicum.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.practicum.dto.ApiError;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class ErrorHandler {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFound(NotFoundException e) {
        log.error("Not found: {}", e.getMessage());
        return ApiError.builder()
                .status("NOT_FOUND")
                .reason("Искомый объект не был найден.")
                .message(e.getMessage())
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleConflict(ConflictException e) {
        log.error("Conflict: {}", e.getMessage());
        return ApiError.builder()
                .status("CONFLICT")
                .reason("Для запрошенной операции условия не выполнены.")
                .message(e.getMessage())
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidation(ValidationException e) {
        log.error("Validation error: {}", e.getMessage());
        return ApiError.builder()
                .status("BAD_REQUEST")
                .reason("Неправильно составленный запрос.")
                .message(e.getMessage())
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        List<String> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> String.format("Field: %s. Error: %s. Value: %s",
                        error.getField(), error.getDefaultMessage(), error.getRejectedValue()))
                .collect(Collectors.toList());

        String message = String.join("; ", errors);
        log.error("Validation error: {}", message);

        return ApiError.builder()
                .errors(errors)
                .status("BAD_REQUEST")
                .reason("Неправильно составленный запрос.")
                .message(message)
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleMissingParams(MissingServletRequestParameterException e) {
        String message = String.format("Missing required parameter: %s", e.getParameterName());
        log.error("Missing parameter: {}", message);
        return ApiError.builder()
                .status("BAD_REQUEST")
                .reason("Неправильно составленный запрос.")
                .message(message)
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = String.format("Invalid parameter: %s. Expected type: %s",
                e.getName(), e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "unknown");
        log.error("Type mismatch: {}", message);
        return ApiError.builder()
                .status("BAD_REQUEST")
                .reason("Неправильно составленный запрос.")
                .message(message)
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleConstraintViolation(ConstraintViolationException e) {
        List<String> errors = e.getConstraintViolations().stream()
                .map(violation -> String.format("Field: %s. Error: %s. Value: %s",
                        violation.getPropertyPath(), violation.getMessage(), violation.getInvalidValue()))
                .collect(Collectors.toList());

        String message = String.join("; ", errors);
        log.error("Нарушение ограничений: {}", message);

        return ApiError.builder()
                .errors(errors)
                .status("BAD_REQUEST")
                .reason("Неправильно составленный запрос.")
                .message(message)
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();
    }


    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.error("Нарушение целостности данных: {}", e.getMessage());
        return ApiError.builder()
                .status("CONFLICT")
                .reason("Было нарушено ограничение целостности.")
                .message(e.getMostSpecificCause().getMessage())
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleException(Exception e) {
        log.error("Внутренняя ошибка: {}", e.getMessage(), e);
        return ApiError.builder()
                .status("INTERNAL_SERVER_ERROR")
                .reason("Внутренняя ошибка сервера.")
                .message(e.getMessage())
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();
    }
}
