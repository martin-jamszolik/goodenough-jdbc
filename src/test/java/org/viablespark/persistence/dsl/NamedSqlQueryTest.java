package org.viablespark.persistence.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class NamedSqlQueryTest {

  @Test
  void createsQueryFromMap() {
    NamedSqlQuery query =
        NamedSqlQuery.raw("SELECT * FROM contractor WHERE sc_key = :id", Map.of("id", 1L));

    assertEquals("SELECT * FROM contractor WHERE sc_key = :id", query.sql());
    assertNotNull(query.params());
  }

  @Test
  void createsQueryFromParameterSource() {
    NamedSqlQuery query =
        NamedSqlQuery.raw(
            "SELECT * FROM contractor WHERE sc_key = :id", new MapSqlParameterSource("id", 1L));

    assertEquals("SELECT * FROM contractor WHERE sc_key = :id", query.sql());
    assertNotNull(query.params());
  }

  @Test
  void rejectsNullArguments() {
    assertThrows(NullPointerException.class, () -> NamedSqlQuery.raw(null, Map.of()));
    NamedSqlQuery query = NamedSqlQuery.raw("SELECT 1", (Map<String, ?>) null);
    assertEquals("SELECT 1", query.sql());
    assertNotNull(query.params());
    assertThrows(
        NullPointerException.class, () -> NamedSqlQuery.raw("SELECT 1", (SqlParameterSource) null));
  }
}
