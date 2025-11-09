package org.viablespark.persistence.validation;

import java.util.Objects;

import org.viablespark.persistence.dsl.SqlQuery;

/**
 * Utility that performs lightweight validation on {@link SqlQuery} instances.
 * The intent is to catch obvious runtime mistakes (for example a mismatch
 * between the number of JDBC placeholders and bound values) during tests or
 * before delegating to {@link org.springframework.jdbc.core.JdbcTemplate}.
 */
public final class SqlQueryValidator {

    private SqlQueryValidator() {
    }

    public static void assertPlaceholderCount(SqlQuery query) {
        Objects.requireNonNull(query, "SqlQuery must not be null");
        String sql = query.sql();
        int placeholderCount = countPlaceholders(sql);
        int valueCount = query.values().length;
        if (placeholderCount != valueCount) {
            throw new IllegalArgumentException(String.format(
                "Placeholder mismatch for SQL [%s]: expected %d values but found %d",
                sql, placeholderCount, valueCount));
        }
    }

    static int countPlaceholders(String sql) {
        if (sql == null || sql.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?') {
                count++;
            }
        }
        return count;
    }
}
