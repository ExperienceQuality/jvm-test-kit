package com.xq.jvmtestkit.cucumber;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.cucumber.datatable.DataTable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Composes nested JSON from Cucumber tables whose column headers are object paths. */
public final class XqJsonTable {
    private static final ObjectMapper JSON = new ObjectMapper();

    private XqJsonTable() {
    }

    /**
     * Converts field-path column headers and one or more value rows to nested JSON.
     * One data row returns an object; multiple data rows return an array of objects.
     * JSON literals are parsed, while unquoted bare text becomes a JSON string.
     */
    public static JsonNode compose(DataTable table) {
        Objects.requireNonNull(table, "table");
        List<List<String>> rows = table.asLists(String.class);
        if (rows.isEmpty() || rows.getFirst().isEmpty()) {
            throw new IllegalArgumentException("Invalid JSON table row 1: expected at least one field-path header");
        }
        if (rows.size() == 1) throw new IllegalArgumentException("Invalid JSON table row 2: expected at least one data row");
        List<Header> headers = new ArrayList<>();
        Map<String, Object> headerShape = new LinkedHashMap<>();
        Map<List<Object>, Map<Integer, Integer>> headerSparseSlots = new IdentityHashMap<>();
        for (String rawHeader : rows.getFirst()) {
            String path = rawHeader == null ? null : rawHeader.trim();
            if (path == null || path.isBlank()) throw rowError(1, "header", "field path must not be blank");
            List<Segment> segments = parsePath(path, 1);
            headers.add(new Header(path, segments));
            insert(headerShape, segments, JsonNodeFactory.instance.nullNode(), path, 1, headerSparseSlots);
        }
        rejectSparseArrays(headerShape, "", headerSparseSlots);
        List<JsonNode> composedRows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            int rowNumber = rowIndex + 1;
            if (row.size() != headers.size()) {
                throw rowError(rowNumber, "row", "expected " + headers.size() + " values to match header columns");
            }
            Map<String, Object> root = new LinkedHashMap<>();
            Map<List<Object>, Map<Integer, Integer>> sparseSlots = new IdentityHashMap<>();
            for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                String rawValue = row.get(columnIndex) == null ? null : row.get(columnIndex).trim();
                int columnNumber = columnIndex + 1;
                if (rawValue == null || rawValue.isBlank()) {
                    throw rowError(rowNumber, "column " + columnNumber, "value must not be blank");
                }
                JsonNode value = parseValue(rawValue, rowNumber, columnNumber);
                Header header = headers.get(columnIndex);
                insert(root, header.segments(), value, header.path(), rowNumber, sparseSlots);
            }
            rejectSparseArrays(root, "", sparseSlots);
            composedRows.add(toJson(root));
        }
        if (composedRows.size() == 1) return composedRows.getFirst();
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        composedRows.forEach(result::add);
        return result;
    }

    private static JsonNode parseValue(String rawValue, int row, int column) {
        if (!isJsonLiteralCandidate(rawValue)) return TextNode.valueOf(rawValue);
        try {
            JsonNode value = JSON.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(rawValue);
            if (value == null) throw rowError(row, "column " + column, "value must be a JSON literal or bare text");
            return value;
        } catch (JsonProcessingException exception) {
            throw rowError(row, "column " + column, "value is malformed JSON");
        }
    }

    private static boolean isJsonLiteralCandidate(String value) {
        char first = value.charAt(0);
        return first == '"' || first == '{' || first == '[' || first == '-'
                || Character.isDigit(first) || "true".equals(value) || "false".equals(value) || "null".equals(value);
    }

    private static void insert(Object root, List<Segment> segments, JsonNode value, String path, int row,
                               Map<List<Object>, Map<Integer, Integer>> sparseSlots) {
        Object current = root;
        for (int index = 0; index < segments.size(); index++) {
            Segment segment = segments.get(index);
            boolean leaf = index == segments.size() - 1;
            Object nextContainer = leaf ? value : newContainer(segments.get(index + 1));
            if (segment.key() != null) {
                if (!(current instanceof Map<?, ?>)) throw rowError(row, "path", "conflicting object and array paths at '" + path + "'");
                @SuppressWarnings("unchecked") Map<String, Object> object = (Map<String, Object>) current;
                String key = segment.key();
                if (leaf) {
                    if (object.containsKey(key)) throw rowError(row, "path", "duplicate or conflicting path '" + path + "'");
                    object.put(key, value);
                } else {
                    if (!object.containsKey(key)) object.put(key, nextContainer);
                    Object child = object.get(key);
                    if (!matches(child, segments.get(index + 1))) throw rowError(row, "path", "conflicting object and array paths at '" + path + "'");
                    current = child;
                }
            } else {
                if (!(current instanceof List<?>)) throw rowError(row, "path", "conflicting object and array paths at '" + path + "'");
                @SuppressWarnings("unchecked") List<Object> array = (List<Object>) current;
                int position = segment.index();
                Map<Integer, Integer> holes = sparseSlots.computeIfAbsent(array, ignored -> new LinkedHashMap<>());
                while (array.size() <= position) {
                    holes.put(array.size(), row);
                    array.add(null);
                }
                if (leaf) {
                    if (array.get(position) != null) throw rowError(row, "path", "duplicate or conflicting path '" + path + "'");
                    array.set(position, value);
                    holes.remove(position);
                } else {
                    Object child = array.get(position);
                    if (child == null) {
                        child = nextContainer;
                        array.set(position, child);
                    }
                    if (!matches(child, segments.get(index + 1))) throw rowError(row, "path", "conflicting object and array paths at '" + path + "'");
                    current = child;
                }
            }
        }
    }

    private static Object newContainer(Segment next) {
        return next.key() != null ? new LinkedHashMap<String, Object>() : new ArrayList<>();
    }

    private static boolean matches(Object value, Segment next) {
        return next.key() != null ? value instanceof Map<?, ?> : value instanceof List<?>;
    }

    private static List<Segment> parsePath(String path, int row) {
        List<Segment> segments = new ArrayList<>();
        int cursor = 0;
        while (cursor < path.length()) {
            int start = cursor;
            while (cursor < path.length() && path.charAt(cursor) != '.' && path.charAt(cursor) != '[') {
                if (path.charAt(cursor) == ']' || path.charAt(cursor) == '\\') {
                    throw rowError(row, "path", "unsupported key syntax in path '" + path + "'");
                }
                cursor++;
            }
            if (start == cursor) throw rowError(row, "path", "invalid path syntax '" + path + "'");
            segments.add(new Segment(path.substring(start, cursor), null));
            while (cursor < path.length() && path.charAt(cursor) == '[') {
                int indexStart = ++cursor;
                while (cursor < path.length() && Character.isDigit(path.charAt(cursor))) cursor++;
                if (indexStart == cursor || cursor >= path.length() || path.charAt(cursor) != ']') {
                    throw rowError(row, "path", "array indexes must use zero-based [n] syntax in path '" + path + "'");
                }
                try {
                    segments.add(new Segment(null, Integer.parseInt(path.substring(indexStart, cursor))));
                } catch (NumberFormatException exception) {
                    throw rowError(row, "path", "array index is too large in path '" + path + "'");
                }
                cursor++;
            }
            if (cursor == path.length()) break;
            if (path.charAt(cursor) != '.' || cursor + 1 == path.length() || path.charAt(cursor + 1) == '.'
                    || path.charAt(cursor + 1) == '[') {
                throw rowError(row, "path", "invalid path syntax '" + path + "'");
            }
            cursor++;
        }
        return segments;
    }

    private static void rejectSparseArrays(Object value, String path, Map<List<Object>, Map<Integer, Integer>> sparseSlots) {
        if (value instanceof List<?> array) {
            for (int index = 0; index < array.size(); index++) {
                Object child = array.get(index);
                if (child == null) {
                    int row = sparseSlots.getOrDefault(array, Map.of()).getOrDefault(index, 2);
                    throw rowError(row, "path", "sparse arrays are not supported at '" + path + "[" + index + "]'");
                }
                rejectSparseArrays(child, path + "[" + index + "]", sparseSlots);
            }
        } else if (value instanceof Map<?, ?> object) {
            object.forEach((key, child) -> rejectSparseArrays(child, path.isEmpty() ? key.toString() : path + "." + key, sparseSlots));
        }
    }

    private static JsonNode toJson(Object value) {
        if (value instanceof JsonNode node) return node;
        if (value instanceof Map<?, ?> object) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            object.forEach((key, child) -> result.set(key.toString(), toJson(child)));
            return result;
        }
        if (value instanceof List<?> array) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            array.forEach(child -> result.add(toJson(child)));
            return result;
        }
        return JSON.valueToTree(value);
    }

    private static IllegalArgumentException rowError(int row, String column, String detail) {
        return new IllegalArgumentException("Invalid JSON table row " + row + ", " + column + " column: " + detail);
    }

    private record Segment(String key, Integer index) {
    }

    private record Header(String path, List<Segment> segments) {
    }
}
