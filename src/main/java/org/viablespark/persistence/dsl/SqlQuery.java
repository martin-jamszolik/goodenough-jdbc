/*
 * Copyright (c) 2023 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.viablespark.persistence.dsl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SqlQuery {

  private enum Mode {
    RAW,
    COMPOSED
  }

  private final Mode mode;
  private final String rawSql;
  private final Object[] rawValues;

  private String selectClause;
  private final List<SqlClause> bodyClauses;
  private final List<WhereClause> whereClauses;
  private final List<OrderBy> orderClauses;
  private Integer limit;
  private Integer offset;
  private String primaryKeyName;

  public SqlQuery() {
    this.mode = Mode.COMPOSED;
    this.rawSql = null;
    this.rawValues = new Object[0];
    this.bodyClauses = new ArrayList<>();
    this.whereClauses = new ArrayList<>();
    this.orderClauses = new ArrayList<>();
  }

  private SqlQuery(String sql, Object[] values) {
    this.mode = Mode.RAW;
    this.rawSql = Objects.requireNonNull(sql, "SQL must not be null");
    this.rawValues = values != null ? values.clone() : new Object[0];
    this.bodyClauses = new ArrayList<>();
    this.whereClauses = new ArrayList<>();
    this.orderClauses = new ArrayList<>();
  }

  public static SqlQuery raw(String sql, Object... values) {
    return new SqlQuery(sql, values);
  }

  public SqlQuery clause(String clause, Object... values) {
    ensureComposable();
    bodyClauses.add(new SqlClause(normalize(clause), sanitize(values)));
    return this;
  }

  public SqlQuery select(String select) {
    ensureComposable();
    this.selectClause = normalize(select);
    return this;
  }

  public SqlQuery selectColumns(String... columns) {
    ensureComposable();
    if (columns == null || columns.length == 0) {
      throw new IllegalArgumentException("At least one column must be specified");
    }
    String joined =
        Arrays.stream(columns)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.joining(", "));
    this.selectClause = "SELECT " + joined;
    return this;
  }

  public SqlQuery selectDistinct(String... columns) {
    ensureComposable();
    if (columns == null || columns.length == 0) {
      throw new IllegalArgumentException("At least one column must be specified");
    }
    String joined =
        Arrays.stream(columns)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.joining(", "));
    this.selectClause = "SELECT DISTINCT " + joined;
    return this;
  }

  public SqlQuery from(String tableExpression) {
    return clause("FROM " + tableExpression);
  }

  public SqlQuery join(String joinExpression) {
    return clause(joinExpression);
  }

  public SqlQuery where(String fragment, Object... values) {
    ensureComposable();
    whereClauses.add(WhereClause.initial(normalize(fragment), sanitize(values)));
    return this;
  }

  public SqlQuery andWhere(String fragment, Object... values) {
    ensureComposable();
    if (whereClauses.isEmpty()) {
      throw new IllegalStateException("andWhere requires at least one preceding where condition");
    }
    whereClauses.add(WhereClause.and(normalize(fragment), sanitize(values)));
    return this;
  }

  public SqlQuery orWhere(String fragment, Object... values) {
    ensureComposable();
    if (whereClauses.isEmpty()) {
      throw new IllegalStateException("orWhere requires at least one preceding where condition");
    }
    whereClauses.add(WhereClause.or(normalize(fragment), sanitize(values)));
    return this;
  }

  public SqlQuery condition(String clause, Object... values) {
    ensureComposable();
    whereClauses.add(WhereClause.raw(clause, sanitize(values)));
    return this;
  }

  public SqlQuery orderBy(String expression) {
    ensureComposable();
    orderClauses.add(OrderBy.raw(expression));
    return this;
  }

  public SqlQuery orderBy(String column, Direction direction) {
    ensureComposable();
    orderClauses.add(OrderBy.from(column, direction));
    return this;
  }

  public SqlQuery limit(int maxRows) {
    ensureComposable();
    if (maxRows < 0) {
      throw new IllegalArgumentException("Limit must be non-negative");
    }
    this.limit = maxRows;
    return this;
  }

  public SqlQuery offset(int rows) {
    ensureComposable();
    if (rows < 0) {
      throw new IllegalArgumentException("Offset must be non-negative");
    }
    this.offset = rows;
    return this;
  }

  public SqlQuery paginate(int maxRows, int startAt) {
    return limit(maxRows).offset(startAt);
  }

  public SqlQuery primaryKey(String pkName) {
    ensureComposable();
    this.primaryKeyName = pkName;
    return this;
  }

  public String sql() {
    if (mode == Mode.RAW) {
      return rawSql;
    }

    StringBuilder sqlBuilder = new StringBuilder();
    appendSegment(sqlBuilder, selectClause);
    for (SqlClause clause : bodyClauses) {
      appendSegment(sqlBuilder, clause.clause());
    }
    if (!whereClauses.isEmpty()) {
      appendSegment(sqlBuilder, buildWhereClause());
    }
    if (!orderClauses.isEmpty()) {
      String order = orderClauses.stream().map(OrderBy::render).collect(Collectors.joining(", "));
      appendSegment(sqlBuilder, "ORDER BY " + order);
    }
    if (limit != null) {
      appendSegment(sqlBuilder, "LIMIT " + limit);
    }
    if (offset != null) {
      appendSegment(sqlBuilder, "OFFSET " + offset);
    }
    return sqlBuilder.toString().trim();
  }

  public Object[] values() {
    if (mode == Mode.RAW) {
      return rawValues.clone();
    }

    List<Object> values = new ArrayList<>();
    for (SqlClause clause : bodyClauses) {
      Collections.addAll(values, clause.values());
    }
    for (WhereClause clause : whereClauses) {
      Collections.addAll(values, clause.values());
    }
    return values.toArray();
  }

  public String getPrimaryKeyName() {
    return primaryKeyName;
  }

  public boolean isRaw() {
    return mode == Mode.RAW;
  }

  private void ensureComposable() {
    if (mode == Mode.RAW) {
      throw new IllegalStateException("Cannot mutate a raw SqlQuery");
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

  private static Object[] sanitize(Object... values) {
    if (values == null) {
      return new Object[0];
    }
    if (values.length == 1 && values[0] == null) {
      return new Object[0];
    }
    return values;
  }

  private static String normalize(String fragment) {
    if (fragment == null) {
      throw new IllegalArgumentException("Clause must not be null");
    }
    return fragment.trim();
  }

  public enum Direction {
    ASC,
    DESC
  }

  private record WhereClause(String connective, String fragment, Object[] values, boolean raw) {
    private WhereClause(String connective, String fragment, Object[] values, boolean raw) {
      this.connective = connective;
      this.fragment = fragment;
      this.values = values != null ? values : new Object[0];
      this.raw = raw;
    }

    static WhereClause initial(String fragment, Object[] values) {
      return new WhereClause(null, fragment, values, false);
    }

    static WhereClause and(String fragment, Object[] values) {
      return new WhereClause("AND", fragment, values, false);
    }

    static WhereClause or(String fragment, Object[] values) {
      return new WhereClause("OR", fragment, values, false);
    }

    static WhereClause raw(String fragment, Object[] values) {
      return new WhereClause(null, fragment, values, true);
    }

    String render(boolean first) {
      if (raw) {
        return fragment;
      }
      if (first || connective == null || connective.isBlank()) {
        return fragment;
      }
      return connective + " " + fragment;
    }
  }

  private record OrderBy(String expression, Direction direction) {
    private OrderBy(String expression, Direction direction) {
      this.expression = expression.trim();
      this.direction = direction;
    }

    static OrderBy raw(String expression) {
      return new OrderBy(expression, null);
    }

    static OrderBy from(String column, Direction direction) {
      return new OrderBy(column, direction == null ? Direction.ASC : direction);
    }

    String render() {
      if (direction == null) {
        return expression;
      }
      return expression + " " + direction.name().toLowerCase();
    }
  }
}
