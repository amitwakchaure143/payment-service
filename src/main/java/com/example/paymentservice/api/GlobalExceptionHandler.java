package com.example.paymentservice.api;

import com.example.paymentservice.domain.InvalidPaymentStateException;
import com.example.paymentservice.domain.OverCaptureException;
import com.example.paymentservice.service.IdempotencyPayloadMismatchException;
import com.example.paymentservice.service.PaymentInProgressException;
import com.example.paymentservice.service.PaymentNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

// Extended so Spring MVC's own exceptions keep their 4xx responses.
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        return problemResponse(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(PaymentNotFoundException ex) {
        return problemResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({PaymentInProgressException.class, InvalidPaymentStateException.class})
    public ResponseEntity<ProblemDetail> handleConflict(RuntimeException ex) {
        return problemResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    // A concurrent duplicate that slipped past the replay lookup: 409, not 500.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDuplicate(DataIntegrityViolationException ex) {
        log.warn("Concurrent duplicate request rejected by unique constraint");
        return problemResponse(HttpStatus.CONFLICT, "A request with this idempotency key is already being processed");
    }

    @ExceptionHandler({IdempotencyPayloadMismatchException.class, OverCaptureException.class})
    public ResponseEntity<ProblemDetail> handleUnprocessable(RuntimeException ex) {
        return problemResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problemResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<ProblemDetail> problemResponse(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
