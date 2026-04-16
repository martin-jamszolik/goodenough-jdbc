package org.viablespark.persistence.dsl;

import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

public final class NamedSqlQuery {
  private final String sql;
  private final SqlParameterSource params;

  private NamedSqlQuery(String sql, SqlParameterSource params) {
    this.sql = Objects.requireNonNull(sql, "SQL must not be null");
    this.params = Objects.requireNonNull(params, "SQL parameters must not be null");
  }

  public static NamedSqlQuery raw(String sql, Map<String, ?> params) {
    return new NamedSqlQuery(sql, new MapSqlParameterSource(params));
  }

  public static NamedSqlQuery raw(String sql, SqlParameterSource params) {
    return new NamedSqlQuery(sql, params);
  }

  public String sql() {
    return sql;
  }

  public SqlParameterSource params() {
    return params;
  }
}
