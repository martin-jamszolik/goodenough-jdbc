package org.viablespark.persistence.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class NamedSqlQueryTest {

  @Test
  void buildsComposedNamedQuery() {
    NamedSqlQuery query =
        new NamedSqlQuery()
            .selectColumns("sc_key", "sc_name")
            .from("contractor")
            .where("sc_key IN (:ids)")
            .andWhere("sc_name <> :excluded")
            .orderBy("sc_key", SqlQuery.Direction.DESC)
            .limit(10)
            .offset(5)
            .param("ids", java.util.List.of(1L, 2L))
            .param("excluded", "Archived");

    assertEquals(
        "SELECT sc_key, sc_name FROM contractor WHERE sc_key IN (:ids) AND sc_name <> :excluded ORDER BY sc_key desc LIMIT 10 OFFSET 5",
        query.sql());
    assertFalse(query.isRaw());
    assertNotNull(query.params());
  }

  @Test
  void supportsNamedQueryPrimaryKeyAndBulkParams() {
    NamedSqlQuery query =
        new NamedSqlQuery()
            .from("contractor")
            .where("sc_key = :id")
            .params(Map.of("id", 1L))
            .primaryKey("sc_key");

    assertEquals("FROM contractor WHERE sc_key = :id", query.sql());
    assertEquals("sc_key", query.getPrimaryKeyName());
  }

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

  @Test
  void rejectsMutatingRawQuery() {
    NamedSqlQuery query = NamedSqlQuery.raw("SELECT 1", Map.of());

    IllegalStateException thrown =
        assertThrows(IllegalStateException.class, () -> query.where("id = :id"));
    assertEquals("Cannot mutate a raw NamedSqlQuery", thrown.getMessage());
  }
}
