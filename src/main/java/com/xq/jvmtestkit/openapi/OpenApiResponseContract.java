package com.xq.jvmtestkit.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xq.jvmtestkit.http.ServiceHttpResponse;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Approved OpenAPI 3.0 response contract for one operation, status, and media type.
 */
public final class OpenApiResponseContract {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> SUPPORTED_PRIMITIVES = Set.of("string", "integer", "number", "boolean");

    private final Operation operation;
    private final int statusCode;
    private final String mediaType;
    private final Map<String, Schema> componentSchemas;
    private final Schema<?> responseSchema;

    private OpenApiResponseContract(
            Operation operation,
            int statusCode,
            String mediaType,
            Map<String, Schema> componentSchemas,
            Schema<?> responseSchema) {
        this.operation = operation;
        this.statusCode = statusCode;
        this.mediaType = mediaType;
        this.componentSchemas = Map.copyOf(componentSchemas);
        this.responseSchema = responseSchema;
    }

    public Operation operation() {
        return operation;
    }

    public int statusCode() {
        return statusCode;
    }

    public String mediaType() {
        return mediaType;
    }

    public static OpenApiResponseContract fromSpec(
            String specContent, Operation operation, int statusCode, String mediaType) {
        Objects.requireNonNull(specContent, "specContent");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(mediaType, "mediaType");
        OpenAPI openApi = parseOpenApi30(specContent);
        Map<String, Schema> componentSchemas = openApi.getComponents() == null
                ? Map.of()
                : openApi.getComponents().getSchemas() == null ? Map.of() : openApi.getComponents().getSchemas();
        Schema<?> schema = resolveResponseSchema(openApi, operation, statusCode, mediaType);
        rejectUnsupportedSchemaFeatures(schema, componentSchemas, new HashSet<>());
        return new OpenApiResponseContract(operation, statusCode, mediaType, componentSchemas, schema);
    }

    public ContractCheck check(ServiceHttpResponse response) {
        Objects.requireNonNull(response, "response");
        if (response.statusCode() != statusCode) {
            return ContractCheck.failure(List.of(new ContractViolation(
                    "status", "expected status " + statusCode + " but received " + response.statusCode())));
        }
        return checkJsonBytes(response.body());
    }

    public ContractCheck checkJson(String jsonBody) {
        Objects.requireNonNull(jsonBody, "jsonBody");
        return checkJsonBytes(jsonBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private ContractCheck checkJsonBytes(byte[] body) {
        try {
            JsonNode json = JSON.readTree(body);
            List<ContractViolation> violations = new ArrayList<>();
            validateNode(json, responseSchema, "$", violations, new HashSet<>());
            return violations.isEmpty() ? ContractCheck.success() : ContractCheck.failure(violations);
        } catch (IOException exception) {
            return ContractCheck.failure(List.of(new ContractViolation("body", "response body is not valid JSON")));
        }
    }

    private static OpenAPI parseOpenApi30(String specContent) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult result = new OpenAPIV3Parser().readContents(specContent, null, options);
        if (result.getOpenAPI() == null) {
            throw new OpenApiContractException("OpenAPI document could not be parsed");
        }
        if (!result.getOpenAPI().getOpenapi().startsWith("3.0")) {
            throw new OpenApiContractException("Only OpenAPI 3.0 documents are supported");
        }
        return result.getOpenAPI();
    }

    private static Schema<?> resolveResponseSchema(
            OpenAPI openApi, Operation operation, int statusCode, String mediaType) {
        var paths = openApi.getPaths();
        if (paths == null || paths.get(operation.path()) == null) {
            throw new OpenApiContractException("Operation path is not defined in the OpenAPI document");
        }
        var pathItem = paths.get(operation.path());
        var apiOperation = switch (operation.method()) {
            case "GET" -> pathItem.getGet();
            case "POST" -> pathItem.getPost();
            case "PUT" -> pathItem.getPut();
            case "PATCH" -> pathItem.getPatch();
            case "DELETE" -> pathItem.getDelete();
            default -> throw new OpenApiContractException("HTTP method is not supported for contract lookup");
        };
        if (apiOperation == null) {
            throw new OpenApiContractException("Operation method is not defined for the path");
        }
        ApiResponse apiResponse = apiOperation.getResponses() == null
                ? null
                : apiOperation.getResponses().get(String.valueOf(statusCode));
        if (apiResponse == null) {
            throw new OpenApiContractException("Response status is not defined for the operation");
        }
        MediaType responseMediaType = apiResponse.getContent() == null ? null : apiResponse.getContent().get(mediaType);
        if (responseMediaType == null || responseMediaType.getSchema() == null) {
            throw new OpenApiContractException("Response media type is not defined for the operation");
        }
        return responseMediaType.getSchema();
    }

    private void validateNode(
            JsonNode value, Schema<?> schema, String path, List<ContractViolation> violations, Set<String> refStack) {
        Schema<?> resolved = resolveSchema(schema, refStack);

        if ("array".equals(resolved.getType())) {
            if (!value.isArray()) {
                violations.add(new ContractViolation(path, "expected array"));
                return;
            }
            Schema<?> items = resolved.getItems();
            if (items == null) {
                violations.add(new ContractViolation(path, "array items schema is missing"));
                return;
            }
            for (int index = 0; index < value.size(); index++) {
                validateNode(value.get(index), items, path + "[" + index + "]", violations, new HashSet<>(refStack));
            }
            return;
        }

        if ("object".equals(resolved.getType())) {
            if (!value.isObject()) {
                violations.add(new ContractViolation(path, "expected object"));
                return;
            }
            if (resolved.getRequired() != null) {
                for (String requiredProperty : resolved.getRequired()) {
                    if (!value.has(requiredProperty)) {
                        violations.add(new ContractViolation(path + "." + requiredProperty, "required property is missing"));
                    }
                }
            }
            if (resolved.getProperties() != null) {
                resolved.getProperties().forEach((name, propertySchema) -> {
                    if (value.has(name)) {
                        validateNode(value.get(name), propertySchema, path + "." + name, violations, new HashSet<>(refStack));
                    }
                });
            }
            return;
        }

        String type = resolved.getType();
        if (type == null) {
            violations.add(new ContractViolation(path, "schema type is missing"));
            return;
        }
        if (!SUPPORTED_PRIMITIVES.contains(type)) {
            violations.add(new ContractViolation(path, "unsupported primitive type " + type));
            return;
        }
        if ("string".equals(type) && !value.isTextual()) {
            violations.add(new ContractViolation(path, "expected string"));
        } else if ("integer".equals(type) && !value.isIntegralNumber()) {
            violations.add(new ContractViolation(path, "expected integer"));
        } else if ("number".equals(type) && !value.isNumber()) {
            violations.add(new ContractViolation(path, "expected number"));
        } else if ("boolean".equals(type) && !value.isBoolean()) {
            violations.add(new ContractViolation(path, "expected boolean"));
        }
    }

    private Schema<?> resolveSchema(Schema<?> schema, Set<String> refStack) {
        if (schema.get$ref() == null) {
            rejectUnsupportedSchemaFeatures(schema, componentSchemas, refStack);
            return schema;
        }
        String ref = schema.get$ref();
        if (ref.startsWith("http://") || ref.startsWith("https://")) {
            throw new OpenApiContractException("External references are not supported");
        }
        if (!ref.startsWith("#/components/schemas/")) {
            throw new OpenApiContractException("Only internal component schema references are supported");
        }
        if (!refStack.add(ref)) {
            throw new OpenApiContractException("Circular schema references are not supported");
        }
        String schemaName = ref.substring("#/components/schemas/".length());
        Schema<?> resolved = componentSchemas.get(schemaName);
        if (resolved == null) {
            throw new OpenApiContractException("Referenced schema is not defined");
        }
        rejectUnsupportedSchemaFeatures(resolved, componentSchemas, refStack);
        if (resolved.get$ref() != null) {
            return resolveSchema(resolved, refStack);
        }
        return resolved;
    }

    private static void rejectUnsupportedSchemaFeatures(
            Schema<?> schema, Map<String, Schema> componentSchemas, Set<String> refStack) {
        if (schema == null) {
            return;
        }
        if (schema.get$ref() != null) {
            String ref = schema.get$ref();
            if (ref.startsWith("http://") || ref.startsWith("https://")) {
                throw new OpenApiContractException("External references are not supported");
            }
            if (ref.startsWith("#/components/schemas/") && refStack.add(ref)) {
                Schema<?> resolved = componentSchemas.get(ref.substring("#/components/schemas/".length()));
                if (resolved != null) {
                    rejectUnsupportedSchemaFeatures(resolved, componentSchemas, refStack);
                }
            }
            return;
        }
        if (schema.getOneOf() != null || schema.getAnyOf() != null || schema.getAllOf() != null) {
            throw new OpenApiContractException("Composition keywords are not supported");
        }
        if (schema.getDiscriminator() != null) {
            throw new OpenApiContractException("Discriminators are not supported");
        }
    }

    public static final class OpenApiContractException extends RuntimeException {
        OpenApiContractException(String message) {
            super(message);
        }
    }
}
