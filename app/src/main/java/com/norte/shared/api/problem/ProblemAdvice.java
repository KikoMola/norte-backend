package com.norte.shared.api.problem;

import java.util.List;

import com.norte.contract.model.ProblemDto;
import com.norte.contract.model.ValidationErrorDto;
import com.norte.shared.domain.error.ConflictException;
import com.norte.shared.domain.error.DomainException;
import com.norte.shared.domain.error.ForbiddenException;
import com.norte.shared.domain.error.NotFoundException;
import com.norte.shared.domain.error.StaleVersionException;
import com.norte.shared.domain.error.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Traduce las excepciones a Problem Details (paso 0.17). Los códigos están en {@code docs/errores.md}.
 */
@RestControllerAdvice
class ProblemAdvice {

	private static final Logger log = LoggerFactory.getLogger(ProblemAdvice.class);

	private final Problems problems;

	ProblemAdvice(Problems problems) {
		this.problems = problems;
	}

	@ExceptionHandler(ValidationException.class)
	ResponseEntity<ProblemDto> validation(ValidationException ex) {
		var errors = ex.violations()
			.stream()
			.map(v -> new ValidationErrorDto(v.field(), v.code()).message(v.message()))
			.toList();
		return problems.response(problems.validation("Datos no válidos", errors));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ProblemDto> beanValidation(MethodArgumentNotValidException ex) {
		List<ValidationErrorDto> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> new ValidationErrorDto(error.getField(), constraintCode(error))
				.message(error.getDefaultMessage()))
			.toList();
		return problems.response(problems.validation("Datos no válidos", errors));
	}

	@ExceptionHandler(NotFoundException.class)
	ResponseEntity<ProblemDto> notFound(NotFoundException ex) {
		return domain(HttpStatus.NOT_FOUND, ex);
	}

	@ExceptionHandler(ConflictException.class)
	ResponseEntity<ProblemDto> conflict(ConflictException ex) {
		return domain(HttpStatus.CONFLICT, ex);
	}

	@ExceptionHandler(ForbiddenException.class)
	ResponseEntity<ProblemDto> forbidden(ForbiddenException ex) {
		return domain(HttpStatus.FORBIDDEN, ex);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ProblemDto> accessDenied(AccessDeniedException ex) {
		return problems.response(problems.problem(HttpStatus.FORBIDDEN, "auth.forbidden",
				"No tienes permiso para esta acción", null));
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ResponseEntity<ProblemDto> optimisticLock(OptimisticLockingFailureException ex) {
		return problems.response(problems.problem(HttpStatus.CONFLICT, "concurrency.conflict",
				"Otro usuario ha modificado este registro", "Recarga los datos y vuelve a intentarlo."));
	}

	@ExceptionHandler(PreconditionFailedException.class)
	ResponseEntity<ProblemDto> preconditionFailed(PreconditionFailedException ex) {
		return problems.response(problems.problem(HttpStatus.PRECONDITION_FAILED, "concurrency.precondition_failed",
				"Otro usuario ha modificado este registro", ex.getMessage()));
	}

	@ExceptionHandler(StaleVersionException.class)
	ResponseEntity<ProblemDto> staleVersion(StaleVersionException ex) {
		return problems.response(problems.problem(HttpStatus.PRECONDITION_FAILED, ex.code(), ex.getMessage(),
				"Recarga los datos y vuelve a intentarlo."));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ProblemDto> tooLarge(MaxUploadSizeExceededException ex) {
		return problems.response(problems.problem(HttpStatus.CONTENT_TOO_LARGE, "request.too_large",
				"El fichero es demasiado grande", null));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ProblemDto> unreadable(HttpMessageNotReadableException ex) {
		return problems.response(problems.problem(HttpStatus.BAD_REQUEST, "request.malformed",
				"El cuerpo de la petición no es un JSON válido", null));
	}

	@ExceptionHandler({ HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class,
			MissingServletRequestParameterException.class, MissingRequestHeaderException.class,
			jakarta.validation.ConstraintViolationException.class,
			InvalidParameterException.class })
	ResponseEntity<ProblemDto> invalidParameter(Exception ex) {
		return problems.response(problems.problem(HttpStatus.BAD_REQUEST, "request.invalid_parameter",
				"Parámetros de la petición no válidos", ex instanceof InvalidParameterException ? ex.getMessage() : null));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDto> unexpected(Exception ex) {
		if (ex instanceof ErrorResponse errorResponse) {
			// Excepciones estándar de Spring MVC (404 de ruta, 405, 415…): se respeta su estado.
			HttpStatusCode status = errorResponse.getStatusCode();
			return problems.response(problems.problem(status, codeFor(status), errorResponse.getBody().getTitle(), null));
		}
		log.error("Unexpected error", ex);
		return problems.response(problems.problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal",
				"Error inesperado", "Si se repite, indica el traceId al soporte."));
	}

	private ResponseEntity<ProblemDto> domain(HttpStatus status, DomainException ex) {
		return problems.response(problems.problem(status, ex.code(), ex.getMessage(), null));
	}

	private static String codeFor(HttpStatusCode status) {
		return switch (status.value()) {
			case 404 -> "resource.not_found";
			case 405 -> "request.method_not_allowed";
			case 406 -> "request.not_acceptable";
			case 415 -> "request.unsupported_media_type";
			default -> status.is4xxClientError() ? "request.invalid" : "internal";
		};
	}

	/** {@code NotNull} → {@code required}, {@code Size} → {@code length}, {@code Pattern} → {@code pattern}… */
	private static String constraintCode(FieldError error) {
		String constraint = error.getCode() == null ? "invalid" : error.getCode();
		return switch (constraint) {
			case "NotNull", "NotBlank", "NotEmpty" -> "required";
			case "Size", "Length" -> "length";
			case "Pattern" -> "pattern";
			case "Email" -> "email";
			case "Min", "DecimalMin", "Positive", "PositiveOrZero" -> "min";
			case "Max", "DecimalMax", "Negative", "NegativeOrZero" -> "max";
			default -> "invalid";
		};
	}

}
