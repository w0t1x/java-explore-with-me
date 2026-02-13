package ru.practicum.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Objects;

import static ru.practicum.util.DateTimeUtil.EWM_FORMATTER;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String REASON_BAD_REQUEST = "Incorrectly made request.";
    private static final String REASON_NOT_FOUND = "The required object was not found.";
    private static final String REASON_CONFLICT = "Integrity constraint has been violated.";
    private static final String REASON_FORBIDDEN = "For the requested operation the conditions are not met.";

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException e) {
        return build(HttpStatus.NOT_FOUND, REASON_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException e) {
        return build(HttpStatus.CONFLICT, REASON_FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException e) {
        return build(HttpStatus.BAD_REQUEST, REASON_BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException e) {
        String msg = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage();
        return build(HttpStatus.CONFLICT, REASON_CONFLICT, msg);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg;
        if (fe != null) {
            msg = String.format("Field: %s. Error: %s. Value: %s",
                    fe.getField(),
                    fe.getDefaultMessage(),
                    Objects.toString(fe.getRejectedValue()));
        } else {
            msg = e.getMessage();
        }
        return build(HttpStatus.BAD_REQUEST, REASON_BAD_REQUEST, msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException e) {
        return build(HttpStatus.BAD_REQUEST, REASON_BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleTypeMismatch(Exception e) {
        return build(HttpStatus.BAD_REQUEST, REASON_BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMessageConversionException.class})
    public ResponseEntity<ApiError> handleMessageConversion(Exception e) {
        return build(HttpStatus.BAD_REQUEST, REASON_BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiError> handleSpringError(ErrorResponseException e) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        String reason = status.is4xxClientError() ? REASON_BAD_REQUEST : "Internal server error.";
        return build(status, reason, e.getMessage());
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ApiError> handleOther(Throwable e) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error.", e.getMessage());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String reason, String message) {
        ApiError apiError = ApiError.builder()
                .errors(Collections.emptyList())
                .message(message)
                .reason(reason)
                .status(status.name())
                .timestamp(LocalDateTime.now().format(EWM_FORMATTER))
                .build();
        return new ResponseEntity<>(apiError, status);
    }
}
