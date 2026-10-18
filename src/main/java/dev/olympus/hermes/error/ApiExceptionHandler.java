package dev.olympus.hermes.error;

import dev.olympus.hermes.storage.RangeNotSatisfiableException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import software.amazon.awssdk.core.exception.SdkException;

/**
 * Maps every error to an RFC 9457 problem detail. The base class already covers the framework exceptions: 400
 * (binding/missing parameters/parts), 404 (no handler), 405, 413 (MaxUploadSizeExceededException), 415.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail notFound(NotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
	}

	@ExceptionHandler(BadRequestException.class)
	ProblemDetail badRequest(BadRequestException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	ProblemDetail conflict(ConflictException ex) {
		return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail integrity(DataIntegrityViolationException ex) {
		log.debug("Constraint violation", ex);
		return problem(HttpStatus.CONFLICT, "Conflict", "The request conflicts with the current state of the resource");
	}

	@ExceptionHandler(UnsupportedFileTypeException.class)
	ProblemDetail unsupportedFile(UnsupportedFileTypeException ex) {
		ProblemDetail pd = problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type", ex.getMessage());
		pd.setProperty("supported", ex.supported());
		return pd;
	}

	@ExceptionHandler(RangeNotSatisfiableException.class)
	ResponseEntity<ProblemDetail> rangeNotSatisfiable(RangeNotSatisfiableException ex) {
		return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
				.header(HttpHeaders.CONTENT_RANGE, "bytes */" + ex.totalSize())
				.header(HttpHeaders.ACCEPT_RANGES, "bytes")
				.body(problem(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "Range Not Satisfiable", ex.getMessage()));
	}

	@ExceptionHandler(SdkException.class)
	ProblemDetail storage(SdkException ex) {
		log.error("Object storage call failed", ex);
		return problem(HttpStatus.BAD_GATEWAY, "Bad Gateway", "Object storage is unavailable");
	}

	/** Typically a client that hung up mid-stream: nothing can be written any more. */
	@ExceptionHandler(IOException.class)
	ProblemDetail io(IOException ex, HttpServletResponse response) {
		if (response.isCommitted()) {
			log.debug("I/O error after response was committed (client disconnect?): {}", ex.toString());
			return null;
		}
		log.error("I/O error", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "Unexpected I/O error");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail unexpected(Exception ex, HttpServletResponse response) {
		log.error("Unhandled error", ex);
		if (response.isCommitted()) {
			return null;
		}
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "Unexpected error");
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail pd = ex.getBody();
		pd.setDetail("Request validation failed");
		pd.setProperty("errors", ex.getBindingResult().getAllErrors().stream()
				.map(e -> Map.of(
						"field", e instanceof FieldError fe ? fe.getField() : e.getObjectName(),
						"message", String.valueOf(e.getDefaultMessage())))
				.toList());
		return handleExceptionInternal(ex, pd, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail pd = ex.getBody();
		pd.setDetail("Request validation failed");
		List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
				.flatMap(r -> r.getResolvableErrors().stream().map(e -> Map.of(
						"field", String.valueOf(r.getMethodParameter().getParameterName()),
						"message", String.valueOf(e.getDefaultMessage()))))
				.toList();
		pd.setProperty("errors", errors);
		return handleExceptionInternal(ex, pd, headers, status, request);
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
		pd.setTitle(title);
		return pd;
	}
}
