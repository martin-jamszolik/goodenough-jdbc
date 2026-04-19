package org.viablespark.persistence.dsl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

public final class NamedSqlQuery {

  private enum Mode {
    RAW,
    COMPOSED
  }

  private final Mode mode;
  private final String rawSql;
  private final SqlParameterSource rawParams;

  private String selectClause;
  private final List<String> bodyClauses;
  private final List<WhereClause> whereClauses;
  private final List<OrderBy> orderClauses;
  private final Map<String, Object> params;
  private Integer limit;
  private Integer offset;
  private String primaryKeyName;

  public NamedSqlQuery() {
    this.mode = Mode.COMPOSED;
    this.rawSql = null;
    this.rawParams = null;
    this.bodyClauses = new ArrayList<>();
    this.whereClauses = new ArrayList<>();
    this.orderClauses = new ArrayList<>();
    this.params = new LinkedHashMap<>();
  }

  private NamedSqlQuery(String sql, SqlParameterSource params) {
    this.mode = Mode.RAW;
    this.rawSql = Objects.requireNonNull(sql, "SQL must not be null");
    this.rawParams = Objects.requireNonNull(params, "SQL parameters must not be null");
    this.bodyClauses = new ArrayList<>();
    this.whereClauses = new ArrayList<>();
    this.orderClauses = new ArrayList<>();
    this.params = new LinkedHashMap<>();
  }

  public static NamedSqlQuery raw(String sql, Map<String, ?> params) {
    return new NamedSqlQuery(
        sql, params == null ? new MapSqlParameterSource() : new MapSqlParameterSource(params));
  }

  public static NamedSqlQuery raw(String sql, SqlParameterSource params) {
    return new NamedSqlQuery(sql, params);
  }

  public NamedSqlQuery clause(String clause) {
    ensureComposable();
    bodyClauses.add(normalize(clause));
    return this;
  }

  public NamedSqlQuery select(String select) {
    ensureComposable();
    this.selectClause = normalize(select);
    return this;
  }

  public NamedSqlQuery selectColumns(String... columns) {
    ensureComposable();
    if (columns == null || columns.length == 0) {
      throw new IllegalArgumentException("At least one column must be specified");
    }
    this.selectClause = "SELECT " + joinColumns(columns);
    return this;
  }

  public NamedSqlQuery selectDistinct(String... columns) {
    ensureComposable();
    if (columns == null || columns.length == 0) {
      throw new IllegalArgumentException("At least one column must be specified");
    }
    this.selectClause = "SELECT DISTINCT " + joinColumns(columns);
    return this;
  }

  public NamedSqlQuery from(String tableExpression) {
    return clause("FROM " + tableExpression);
  }

  public NamedSqlQuery join(String joinExpression) {
    return clause(joinExpression);
  }

  public NamedSqlQuery where(String fragment) {
    ensureComposable();
    whereClauses.add(WhereClause.initial(normalize(fragment)));
    return this;
  }

  public NamedSqlQuery andWhere(String fragment) {
    ensureComposable();
    if (whereClauses.isEmpty()) {
      throw new IllegalStateException("andWhere requires at least one preceding where condition");
    }
    whereClauses.add(WhereClause.and(normalize(fragment)));
    return this;
  }

  public NamedSqlQuery orWhere(String fragment) {
    ensureComposable();
    if (whereClauses.isEmpty()) {
      throw new IllegalStateException("orWhere requires at least one preceding where condition");
    }
    whereClauses.add(WhereClause.or(normalize(fragment)));
    return this;
  }

  public NamedSqlQuery condition(String clause) {
    ensureComposable();
    whereClauses.add(WhereClause.raw(normalize(clause)));
    return this;
  }

  public NamedSqlQuery orderBy(String expression) {
    ensureComposable();
    orderClauses.add(OrderBy.raw(expression));
    return this;
  }

  public NamedSqlQuery orderBy(String column, SqlQuery.Direction direction) {
    ensureComposable();
    orderClauses.add(OrderBy.from(column, direction));
    return this;
  }

  public NamedSqlQuery limit(int maxRows) {
    ensureComposable();
    if (maxRows < 0) {
      throw new IllegalArgumentException("Limit must be non-negative");
    }
    this.limit = maxRows;
    return this;
  }

  public NamedSqlQuery offset(int rows) {
    ensureComposable();
    if (rows < 0) {
      throw new IllegalArgumentException("Offset must be non-negative");
    }
    this.offset = rows;
    return this;
  }

  public NamedSqlQuery paginate(int maxRows, int startAt) {
    return limit(maxRows).offset(startAt);
  }

  public NamedSqlQuery primaryKey(String pkName) {
    ensureComposable();
    this.primaryKeyName = pkName;
    return this;
  }

  public NamedSqlQuery param(String name, Object value) {
    ensureComposable();
    params.put(normalizeParamName(name), value);
    return this;
  }

  public NamedSqlQuery params(Map<String, ?> values) {
    ensureComposable();
    if (values == null) {
      return this;
    }
    values.forEach(this::param);
    return this;
  }

  public String sql() {
    if (mode == Mode.RAW) {
      return rawSql;
    }

    StringBuilder sqlBuilder = new StringBuilder();
    appendSegment(sqlBuilder, selectClause);
    for (String clause : bodyClauses) {
      appendSegment(sqlBuilder, clause);
    }
    if (!whereClauses.isEmpty()) {
      appendSegment(sqlBuilder, buildWhereClause());
    }
    if (!orderClauses.isEmpty()) {
      appendSegment(
          sqlBuilder,
          "ORDER BY "
              + orderClauses.stream().map(OrderBy::render).collect(Collectors.joining(", ")));
    }
    if (limit != null) {
      appendSegment(sqlBuilder, "LIMIT " + limit);
    }
    if (offset != null) {
      appendSegment(sqlBuilder, "OFFSET " + offset);
    }
    return sqlBuilder.toString().trim();
  }

  public SqlParameterSource params() {
    if (mode == Mode.RAW) {
      return rawParams;
    }
    return new MapSqlParameterSource(params);
  }

  public String getPrimaryKeyName() {
    return primaryKeyName;
  }

  public boolean isRaw() {
    return mode == Mode.RAW;
  }

  private void ensureComposable() {
    if (mode == Mode.RAW) {
      throw new IllegalStateException("Cannot mutate a raw NamedSqlQuery");
    }
  }

  private String buildWhereClause() {
    StringBuilder builder = new StringBuilder("WHERE ");
    boolean first = true;
    for (WhereClause clause : whereClauses) {
      if (!first) {
        builder.append(' ');
      }
      builder.append(clause.render(first));
      first = false;
    }
    return builder.toString();
  }

  private static void appendSegment(StringBuilder builder, String segment) {
    if (segment == null || segment.isBlank()) {
      return;
    }
    if (builder.length() > 0 && !Character.isWhitespace(builder.charAt(builder.length() - 1))) {
      builder.append(' ');
    }
    builder.append(segment.trim());
  }

  private static String joinColumns(String... columns) {
    String joined =
        Arrays.stream(columns)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.joining(", "));
    if (joined.isEmpty()) {
      throw new IllegalArgumentException("At least one column must be specified");
    }
    return joined;
  }

  private static String normalize(String fragment) {
    if (fragment == null) {
      throw new IllegalArgumentException("Clause must not be null");
    }
    return fragment.trim();
  }

  private static String normalizeParamName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Parameter name must not be blank");
    }
    return name.trim();
  }

  private record WhereClause(String connective, String fragment, boolean raw) {
    static WhereClause initial(String fragment) {
      return new WhereClause(null, fragment, false);
    }

    static WhereClause and(String fragment) {
      return new WhereClause("AND", fragment, false);
    }

    static WhereClause or(String fragment) {
      return new WhereClause("OR", fragment, false);
    }

    static WhereClause raw(String fragment) {
      return new WhereClause(null, fragment, true);
    }

    String render(boolean first) {
      if (raw || first || connective == null || connective.isBlank()) {
        return fragment;
      }
      return connective + " " + fragment;
    }
  }

  private record OrderBy(String expression, SqlQuery.Direction direction) {
    static OrderBy raw(String expression) {
      return new OrderBy(expression.trim(), null);
    }

    static OrderBy from(String column, SqlQuery.Direction direction) {
      return new OrderBy(column.trim(), direction == null ? SqlQuery.Direction.ASC : direction);
    }

    String render() {
      if (direction == null) {
        return expression;
      }
      return expression + " " + direction.name().toLowerCase();
    }
  }
}
