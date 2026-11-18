package org.viablespark.persistence.validation;

import java.util.Objects;
import org.viablespark.persistence.dsl.SqlQuery;

/**
 * Utility that performs lightweight validation on {@link SqlQuery} instances. The intent is to
 * catch obvious runtime mistakes (for example a mismatch between the number of JDBC placeholders
 * and bound values) during tests or before delegating to {@link
 * org.springframework.jdbc.core.JdbcTemplate}.
 */
public final class SqlQueryValidator {

  private SqlQueryValidator() {}

  public static void assertPlaceholderCount(SqlQuery query) {
    Objects.requireNonNull(query, "SqlQuery must not be null");
    String sql = query.sql();
    int placeholderCount = countPlaceholders(sql);
    int valueCount = query.values().length;
    if (placeholderCount != valueCount) {
      throw new IllegalArgumentException(
          String.format(
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
      char current = sql.charAt(i);
      if (current == '\'' || current == '"') {
        i = skipQuoted(sql, i, current);
      } else if (current == '-' && hasNext(sql, i, '-')) {
        int newline = sql.indexOf('\n', i + 2);
        if (newline < 0) {
          break;
        }
        i = newline;
      } else if (current == '/' && hasNext(sql, i, '*')) {
        i = skipBlockComment(sql, i);
      } else if (current == '$') {
        String delimiter = dollarQuoteDelimiter(sql, i);
        if (delimiter != null) {
          int end = sql.indexOf(delimiter, i + delimiter.length());
          if (end < 0) {
            break;
          }
          i = end + delimiter.length() - 1;
        }
      } else if (current == '?') {
        if (hasNext(sql, i, '?')) {
          i++;
        } else if (!hasNext(sql, i, '|') && !hasNext(sql, i, '&')) {
          count++;
        }
      }
    }
    return count;
  }

  private static int skipQuoted(String sql, int start, char quote) {
    for (int i = start + 1; i < sql.length(); i++) {
      if (sql.charAt(i) == '\\' && i + 1 < sql.length()) {
        i++;
      } else if (sql.charAt(i) == quote) {
        if (hasNext(sql, i, quote)) {
          i++;
        } else {
          return i;
        }
      }
    }
    return sql.length() - 1;
  }

  private static int skipBlockComment(String sql, int start) {
    int depth = 1;
    for (int i = start + 2; i < sql.length() - 1; i++) {
      if (sql.charAt(i) == '/' && hasNext(sql, i, '*')) {
        depth++;
        i++;
      } else if (sql.charAt(i) == '*' && hasNext(sql, i, '/')) {
        depth--;
        if (depth == 0) {
          return i + 1;
        }
        i++;
      }
    }
    return sql.length() - 1;
  }

  private static String dollarQuoteDelimiter(String sql, int start) {
    int end = sql.indexOf('$', start + 1);
    if (end < 0) {
      return null;
    }
    for (int i = start + 1; i < end; i++) {
      char character = sql.charAt(i);
      if (!Character.isLetterOrDigit(character) && character != '_') {
        return null;
      }
    }
    return sql.substring(start, end + 1);
  }

  private static boolean hasNext(String sql, int index, char expected) {
    return index + 1 < sql.length() && sql.charAt(index + 1) == expected;
  }
}
