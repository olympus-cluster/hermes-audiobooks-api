package dev.olympus.hermes.config;

/**
 * References to the shared problem-detail responses registered in {@link OpenApiConfig}, for
 * {@code @ApiResponse(responseCode = "404", ref = ApiErrors.NOT_FOUND_REF)}.
 */
public final class ApiErrors {

	static final String BAD_REQUEST = "BadRequest";
	static final String NOT_FOUND = "NotFound";
	static final String CONFLICT = "Conflict";
	static final String CONTENT_TOO_LARGE = "ContentTooLarge";
	static final String UNSUPPORTED_MEDIA_TYPE = "UnsupportedMediaType";
	static final String RANGE_NOT_SATISFIABLE = "RangeNotSatisfiable";
	static final String BAD_GATEWAY = "BadGateway";

	private static final String PREFIX = "#/components/responses/";

	public static final String BAD_REQUEST_REF = PREFIX + BAD_REQUEST;
	public static final String NOT_FOUND_REF = PREFIX + NOT_FOUND;
	public static final String CONFLICT_REF = PREFIX + CONFLICT;
	public static final String CONTENT_TOO_LARGE_REF = PREFIX + CONTENT_TOO_LARGE;
	public static final String UNSUPPORTED_MEDIA_TYPE_REF = PREFIX + UNSUPPORTED_MEDIA_TYPE;
	public static final String RANGE_NOT_SATISFIABLE_REF = PREFIX + RANGE_NOT_SATISFIABLE;
	public static final String BAD_GATEWAY_REF = PREFIX + BAD_GATEWAY;

	private ApiErrors() {
	}
}
