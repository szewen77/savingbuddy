package my.savingbuddy.web;

import my.savingbuddy.api.Dtos.ErrorResponse;
import my.savingbuddy.service.NotFoundException;
import my.savingbuddy.service.SetupService.AlreadyConfiguredException;
import my.savingbuddy.service.SetupService.InvalidSetupException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalid(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + " " + f.getDefaultMessage()).toList();
        return new ErrorResponse("Validation failed", errors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse unreadable(Exception ex) {
        return new ErrorResponse("Malformed request", List.of());
    }

    @ExceptionHandler(AlreadyConfiguredException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse alreadyConfigured(AlreadyConfiguredException ex) {
        return new ErrorResponse(ex.getMessage(), List.of());
    }

    @ExceptionHandler(InvalidSetupException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidSetup(InvalidSetupException ex) {
        return new ErrorResponse(ex.getMessage(), List.of());
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(NotFoundException ex) {
        return new ErrorResponse(ex.getMessage(), List.of());
    }
}
