package my.savingbuddy.web;

import my.savingbuddy.api.Dtos.ErrorResponse;
import my.savingbuddy.security.LoginRateLimiter.TooManyLoginAttemptsException;
import my.savingbuddy.security.RegistrationPolicy.RegistrationNotAllowedException;
import my.savingbuddy.service.InviteService.TooManyInvitesException;
import my.savingbuddy.service.AuthService.EmailTakenException;
import my.savingbuddy.service.AuthService.IncorrectPasswordException;
import my.savingbuddy.service.NotFoundException;
import org.springframework.security.core.AuthenticationException;
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

    /** Needs a header, so it returns a ResponseEntity rather than using @ResponseStatus. */
    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> tooManyAttempts(TooManyLoginAttemptsException ex) {
        return org.springframework.http.ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(org.springframework.http.HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
            .body(new ErrorResponse(ex.getMessage(), List.of()));
    }

    @ExceptionHandler(IncorrectPasswordException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse incorrectPassword(IncorrectPasswordException ex) {
        return new ErrorResponse(ex.getMessage(), List.of());
    }

    @ExceptionHandler(TooManyInvitesException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse tooManyInvites(TooManyInvitesException ex) {
        return new ErrorResponse(ex.getMessage(), List.of());
    }

    @ExceptionHandler(RegistrationNotAllowedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse registrationNotAllowed(RegistrationNotAllowedException ex) {
        return new ErrorResponse(ex.getMessage(), List.of());
    }

    @ExceptionHandler(EmailTakenException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse emailTaken(EmailTakenException ex) {
        return new ErrorResponse(ex.getMessage(), List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse badCredentials(AuthenticationException ex) {
        // One message for wrong email and wrong password alike — no user enumeration.
        return new ErrorResponse("Email or password is incorrect", List.of());
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
