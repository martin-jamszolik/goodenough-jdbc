package org.viablespark.persistence.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.viablespark.persistence.dsl.SqlQuery;

class SqlQueryValidatorTest {

  @Test
  void acceptsMatchingPlaceholders() {
    SqlQuery query = new SqlQuery().where("dist >= ?", 10);
    assertDoesNotThrow(() -> SqlQueryValidator.assertPlaceholderCount(query));
    assertEquals(1, SqlQueryValidator.countPlaceholders(query.sql()));
  }

  @Test
  void rejectsMismatchedPlaceholders() {
    SqlQuery query = SqlQuery.raw("SELECT * FROM est_proposal WHERE dist >= ? AND sc_key = ?", 10);
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> SqlQueryValidator.assertPlaceholderCount(query));
    assertTrue(thrown.getMessage().contains("expected 2 values but found 1"));
  }

  @Test
  void rejectsNullQuery() {
    NullPointerException thrown =
        assertThrows(NullPointerException.class, () -> SqlQueryValidator.assertPlaceholderCount(null));
    assertEquals("SqlQuery must not be null", thrown.getMessage());
  }

  @Test
  void countsEmptyAndQuestionFreeSql() {
    assertEquals(0, SqlQueryValidator.countPlaceholders(null));
    assertEquals(0, SqlQueryValidator.countPlaceholders(""));
    assertEquals(0, SqlQueryValidator.countPlaceholders("SELECT * FROM contractor"));
  }
}
