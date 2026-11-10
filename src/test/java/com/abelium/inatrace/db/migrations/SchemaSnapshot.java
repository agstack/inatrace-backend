package com.abelium.inatrace.db.migrations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Captures MySQL schema metadata without reading application data. */
final class SchemaSnapshot {
    private static final ObjectMapper JSON = new ObjectMapper();

    private SchemaSnapshot() { }

    static byte[] capture(DataSource dataSource) throws Exception {
        Map<String, List<Map<String, Object>>> snapshot = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            snapshot.put("tables", rows(connection, "SELECT TABLE_NAME, ENGINE, TABLE_COLLATION "
                    + "FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME"));
            snapshot.put("columns", rows(connection, "SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE, "
                    + "IS_NULLABLE, COLUMN_DEFAULT, EXTRA, CHARACTER_SET_NAME, COLLATION_NAME, COLUMN_COMMENT "
                    + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME, ORDINAL_POSITION"));
            snapshot.put("indexes", rows(connection, "SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME, "
                    + "SUB_PART, INDEX_TYPE FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() "
                    + "ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX"));
            snapshot.put("foreignKeys", rows(connection, "SELECT k.TABLE_NAME, k.CONSTRAINT_NAME, k.ORDINAL_POSITION, "
                    + "k.COLUMN_NAME, k.REFERENCED_TABLE_NAME, k.REFERENCED_COLUMN_NAME, r.UPDATE_RULE, r.DELETE_RULE "
                    + "FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r "
                    + "ON r.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND r.TABLE_NAME=k.TABLE_NAME AND r.CONSTRAINT_NAME=k.CONSTRAINT_NAME "
                    + "WHERE k.CONSTRAINT_SCHEMA=DATABASE() AND k.REFERENCED_TABLE_NAME IS NOT NULL "
                    + "ORDER BY k.TABLE_NAME, k.CONSTRAINT_NAME, k.ORDINAL_POSITION"));
        }
        StringBuilder output = new StringBuilder("{\n");
        int section = 0;
        for (Map.Entry<String, List<Map<String, Object>>> entry : snapshot.entrySet()) {
            if (section++ > 0) output.append(",\n");
            output.append("  ").append(JSON.writeValueAsString(entry.getKey())).append(": [\n");
            for (int i = 0; i < entry.getValue().size(); i++) {
                if (i > 0) output.append(",\n");
                output.append("    ").append(JSON.writeValueAsString(entry.getValue().get(i)));
            }
            output.append("\n  ]");
        }
        return output.append("\n}\n").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static List<Map<String, Object>> rows(Connection connection, String sql) throws Exception {
        List<Map<String, Object>> values = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            ResultSetMetaData metadata = result.getMetaData();
            while (result.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    row.put(metadata.getColumnLabel(i), result.getObject(i));
                }
                values.add(row);
            }
        }
        return values;
    }

    static void write(Path path, byte[] snapshot) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, snapshot);
    }

    static List<String> differences(byte[] expected, byte[] actual) throws Exception {
        List<String> result = new ArrayList<>();
        compare(JSON.readTree(expected), JSON.readTree(actual), "", result);
        return result;
    }

    private static void compare(JsonNode expected, JsonNode actual, String path, List<String> result) {
        if (result.size() >= 20 || expected.equals(actual)) return;
        if (expected.isObject() && actual.isObject()) {
            TreeSet<String> keys = new TreeSet<>();
            expected.fieldNames().forEachRemaining(keys::add);
            actual.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) {
                JsonNode left = expected.get(key), right = actual.get(key);
                if (left == null || right == null) result.add(path + "/" + key + ": " + left + " -> " + right);
                else compare(left, right, path + "/" + key, result);
                if (result.size() >= 20) return;
            }
        } else if (expected.isArray() && actual.isArray()) {
            if (expected.size() != actual.size()) result.add(path + ": length " + expected.size() + " -> " + actual.size());
            for (int i = 0; i < Math.min(expected.size(), actual.size()) && result.size() < 20; i++) {
                compare(expected.get(i), actual.get(i), path + "/" + i, result);
            }
        } else {
            result.add(path + ": " + expected + " -> " + actual);
        }
    }
}
