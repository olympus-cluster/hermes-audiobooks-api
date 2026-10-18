package dev.olympus.hermes.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API metadata plus the shared error responses. Operations reference the responses below by name (see
 * {@link ApiErrors}); the generic responses springdoc would derive from the exception handler are disabled
 * ({@code springdoc.override-with-generic-response=false}) so every operation lists only the codes it can return.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

	private static final String PROBLEM_JSON = "application/problem+json";

	@Bean
	OpenAPI hermesOpenApi() {
		Components components = new Components()
				.addSchemas("FieldError", new ObjectSchema()
						.addProperty("field", new StringSchema().description("Offending field or parameter"))
						.addProperty("message", new StringSchema())
						.required(List.of("field", "message")))
				.addSchemas("ProblemDetail", problemDetailSchema());
		addProblem(components, ApiErrors.BAD_REQUEST, "Invalid request: validation failed (see `errors`), malformed body "
				+ "or parameter, missing multipart part, empty file, or a rule violation (see `detail`)");
		addProblem(components, ApiErrors.NOT_FOUND, "The audiobook, chapter or stored object does not exist");
		addProblem(components, ApiErrors.CONFLICT, "The chapter position is already used by another chapter");
		addProblem(components, ApiErrors.CONTENT_TOO_LARGE, "The upload exceeds the configured maximum size");
		addProblem(components, ApiErrors.UNSUPPORTED_MEDIA_TYPE,
				"Wrong request content type, or the uploaded file type is not accepted (see `supported`)");
		addProblem(components, ApiErrors.RANGE_NOT_SATISFIABLE,
				"The byte range cannot be satisfied; Content-Range carries `bytes */<size>`");
		addProblem(components, ApiErrors.BAD_GATEWAY, "Object storage is unavailable");
		return new OpenAPI()
				.info(new Info()
						.title("Hermes Audiobooks API")
						.version("v1")
						.description("Manage and stream audiobooks. Errors use RFC 9457 problem details "
								+ "(`application/problem+json`)."))
				.components(components);
	}

	@SuppressWarnings("rawtypes")
	private static Schema problemDetailSchema() {
		return new ObjectSchema()
				.description("RFC 9457 problem details")
				.addProperty("type", new StringSchema().format("uri").example("about:blank"))
				.addProperty("title", new StringSchema().example("Not Found"))
				.addProperty("status", new IntegerSchema().format("int32").example(404))
				.addProperty("detail", new StringSchema())
				.addProperty("instance", new StringSchema().format("uri"))
				.addProperty("errors", new ArraySchema()
						.items(new Schema<>().$ref("#/components/schemas/FieldError"))
						.description("Per-field validation errors (400 only)"))
				.addProperty("supported", new ArraySchema().items(new StringSchema())
						.description("Accepted file content types (415 for uploads only)"))
				.required(List.of("type", "title", "status"));
	}

	private static void addProblem(Components components, String name, String description) {
		components.addResponses(name, new ApiResponse()
				.description(description)
				.content(new Content().addMediaType(PROBLEM_JSON, new MediaType()
						.schema(new Schema<>().$ref("#/components/schemas/ProblemDetail")))));
	}
}
