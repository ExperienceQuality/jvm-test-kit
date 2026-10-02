package com.xq.jvmtestkit.cucumber;

import io.cucumber.datatable.DataTable;
import io.cucumber.datatable.DataTableTypeRegistry;
import io.cucumber.datatable.DataTableTypeRegistryTableConverter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XqJsonTableTest {
    @Test
    void composesNestedObjectFromPathHeadersAndParsesJsonLiteralsAndBareText() {
        var result = XqJsonTable.compose(table(
                List.of("customer.id", "customer.profile.active", "items[0].sku", "items[0].quantity",
                        "metadata", "enabled", "empty", "values", "amount"),
                List.of("cust-123", "true", "SKU-1", "2", "{\"source\":\"e2e\"}", "false", "null", "[1,2,3]", "12.50")
        ));

        assertEquals("cust-123", result.at("/customer/id").asText());
        assertEquals("true", result.at("/customer/profile/active").toString());
        assertEquals("SKU-1", result.at("/items/0/sku").asText());
        assertEquals("2", result.at("/items/0/quantity").toString());
        assertEquals("\"e2e\"", result.at("/metadata/source").toString());
        assertEquals("false", result.at("/enabled").toString());
        assertEquals("null", result.at("/empty").toString());
        assertEquals("[1,2,3]", result.at("/values").toString());
        assertEquals("12.5", result.at("/amount").toString());
    }

    @Test
    void returnsArrayWhenTableContainsMoreThanOneDataRow() {
        var result = XqJsonTable.compose(table(
                List.of("customer.id", "items[0].sku", "items[0].quantity"),
                List.of("cust-1", "SKU-1", "2"),
                List.of("cust-2", "SKU-2", "1")
        ));
        assertTrue(result.isArray());
        assertEquals(2, result.size());
        assertEquals("cust-1", result.get(0).at("/customer/id").asText());
        assertEquals("SKU-2", result.get(1).at("/items/0/sku").asText());
    }

    @Test
    void rejectsInvalidHeadersRowShapesBlankValuesAndMalformedJsonWithDiagnostics() {
        assertThrows(IllegalArgumentException.class, () -> XqJsonTable.compose(table(List.of(" "))));
        assertThrows(IllegalArgumentException.class, () -> XqJsonTable.compose(table(List.of("name"))));
        var blank = assertThrows(IllegalArgumentException.class,
                () -> XqJsonTable.compose(table(List.of("name", "active"), List.of("x", " "))));
        assertTrue(blank.getMessage().contains("row 2, column 2"));
        var malformed = assertThrows(IllegalArgumentException.class,
                () -> XqJsonTable.compose(table(List.of("payload"), List.of("{\"broken\":}"))));
        assertTrue(malformed.getMessage().contains("row 2, column 1"));
        assertThrows(IllegalArgumentException.class,
                () -> XqJsonTable.compose(table(List.of("payload"), List.of("1 2"))));
    }

    @Test
    void rejectsDuplicateConflictingSparseAndUnsupportedHeaderPaths() {
        assertInvalid(List.of("user.name", "user.name"), "duplicate");
        assertInvalid(List.of("user", "user.name"), "conflicting");
        assertInvalid(List.of("items[1].sku"), "sparse");
        assertInvalid(List.of("user.first.name", "user[0]"), "conflicting");
        assertInvalid(List.of("user.first\\.name"), "unsupported key syntax");
    }

    private static IllegalArgumentException assertInvalid(List<String> headers, String messagePart) {
        var error = assertThrows(IllegalArgumentException.class,
                () -> XqJsonTable.compose(table(headers, headers.stream().map(ignored -> "x").toList())));
        assertTrue(error.getMessage().contains(messagePart), error.getMessage());
        return error;
    }

    @SafeVarargs
    private static DataTable table(List<String>... rows) {
        return DataTable.create(List.of(rows), new DataTableTypeRegistryTableConverter(
                new DataTableTypeRegistry(Locale.ENGLISH)));
    }
}
